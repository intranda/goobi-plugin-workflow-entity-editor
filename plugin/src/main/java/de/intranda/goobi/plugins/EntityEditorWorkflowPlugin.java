package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import javax.faces.event.AjaxBehaviorEvent;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;
import org.goobi.vocabulary.Definition;
import org.goobi.vocabulary.Field;
import org.goobi.vocabulary.VocabRecord;
import org.goobi.vocabulary.Vocabulary;

import de.intranda.goobi.plugins.model.BreadcrumbItem;
import de.intranda.goobi.plugins.model.Entity;
import de.intranda.goobi.plugins.model.EntityConfig;
import de.intranda.goobi.plugins.model.EntityType;
import de.intranda.goobi.plugins.model.MetadataField;
import de.intranda.goobi.plugins.model.MetadataField.SourceField;
import de.intranda.goobi.plugins.model.Relationship;
import de.intranda.goobi.plugins.model.RelationshipType;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.exceptions.UghHelperException;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.VocabularyManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.UGHException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j2
public class EntityEditorWorkflowPlugin implements IWorkflowPlugin, IPlugin {

    private static final long serialVersionUID = 8882911907364782646L;

    @Getter
    private String title = "intranda_workflow_entity_editor";

    @Getter
    private PluginType type = PluginType.Workflow;

    @Getter
    private String gui = "/uii/plugin_workflow_entity_editor.xhtml";

    // contains the last opened elements
    @Getter
    private transient List<BreadcrumbItem> breadcrumbList = new ArrayList<>();

    // select a breadcrumb to open
    @Getter
    @Setter
    private transient BreadcrumbItem selectedBreadcrumb;

    // configuration object
    private transient EntityConfig configuration;

    // current field for vocabulary search
    @Getter
    @Setter
    private transient MetadataField searchField;

    // search value for vocabularies
    @Getter
    @Setter
    private String searchValue;

    // search value for sources
    @Getter
    @Setter
    private String sourceSearchValue;

    // display all vocabulary records or
    @Getter
    private boolean showNotHits;

    // records found in vocabulary search
    @Getter
    private List<VocabRecord> records;

    // selected record to import
    @Getter
    @Setter
    private VocabRecord selectedVocabularyRecord;

    // selected field to add sources
    @Getter
    @Setter
    private transient MetadataField currentField;
    // list of all sources
    @Getter
    private List<VocabRecord> sources;

    private Vocabulary sourceVocabulary;

    // selected sources to add
    @Getter
    @Setter
    private VocabRecord selectedSource;

    // page range within the source
    @Getter
    @Setter
    private String pages = "";

    @Getter
    @Setter
    private String mainScrollPosition;

    @Getter
    @Setter
    private String sourceMode;

    @Getter
    private transient Entity entity;

    @Getter
    @Setter
    private String entitySearch;

    @Getter
    private transient List<Entity> entities = new ArrayList<>();

    @Getter
    @Setter
    private transient EntityType entityType;

    @Getter
    private transient List<RelationshipType> relationshipTypes = new ArrayList<>();

    @Getter
    @Setter
    private transient Entity selectedEntity;
    @Getter
    @Setter
    private transient RelationshipType selectedRelationship;

    @Getter
    @Setter
    private String relationshipStartDate;

    @Getter
    @Setter
    private String relationshipEndDate;

    @Getter
    @Setter
    private String relationshipData;

    @Getter
    @Setter
    private Prefs prefs;

    /**
     * Constructor
     */
    public EntityEditorWorkflowPlugin() {
        XMLConfiguration config = ConfigPlugins.getPluginConfig(title);
        config.setExpressionEngine(new XPathExpressionEngine());

        configuration = new EntityConfig(config);

        sourceVocabulary = VocabularyManager.getVocabularyById(configuration.getSourceVocabularyId());
    }

    /**
     * Close the current element and open the selected breadcrumb
     * 
     * {@link selectedBreadcrumb} must be set first
     * 
     * @return
     */

    public String loadSelectedBreadcrumb() {
        // save current entity
        if (entity != null) {
            entity.saveEntity();
        }
        //  check if dashboard was selected -> exit to start page
        if (0 == selectedBreadcrumb.getProcessId() && "Dashboard".equals(selectedBreadcrumb.getEntityName())) {
            return exitPlugin();
        }
        // load selected data

        Process currentProcess = ProcessManager.getProcessById(selectedBreadcrumb.getProcessId());
        prefs = currentProcess.getRegelsatz().getPreferences();
        entity = new Entity(configuration, currentProcess);

        // create breadcrumb item for new entity
        boolean isAdded = false;
        for (BreadcrumbItem bce : breadcrumbList) {
            if (bce.getProcessId() == entity.getCurrentProcess().getId().intValue()) {
                isAdded = true;
            }

        }
        if (!isAdded) {
            BreadcrumbItem item = new BreadcrumbItem(entity.getCurrentType().getName(), entity.getEntityName(), entity.getCurrentProcess().getId(),
                    entity.getCurrentType().getColor(), entity.getCurrentType().getIcon());
            breadcrumbList.add(item);
        }

        return "";
    }

    public void removeBreadcrumb(BreadcrumbItem breadcrumb) {
        breadcrumbList.remove(breadcrumb);

    }

    public String exitPlugin() {
        return "/uii/index.xhtml";
    }

    /**
     * Search within a vocabulary
     * 
     * {@link searchField} must be set first
     */

    public void searchVocabulary() {

        List<StringPair> data = new ArrayList<>();

        for (String field : searchField.getConfigField().getSearchFields()) {
            data.add(new StringPair(field, sourceSearchValue));
        }

        records = VocabularyManager.findRecords(searchField.getConfigField().getVocabularyName(), data);

        if (records == null || records.isEmpty()) {
            showNotHits = true;
        } else {
            showNotHits = false;
        }
        Collections.sort(records);
    }

    /**
     * Import data from selected vocabulary record
     * 
     */
    public void importVocabularyData() {
        Metadata md = searchField.getMetadata();
        for (Field field : selectedVocabularyRecord.getFields()) {
            if (field.getDefinition().isMainEntry()) {
                md.setValue(field.getValue());
            }
        }

        if (StringUtils.isNotBlank(ConfigurationHelper.getInstance().getGoobiAuthorityServerUser())
                && StringUtils.isNotBlank(ConfigurationHelper.getInstance().getGoobiAuthorityServerUrl())) {
            md.setAutorityFile(searchField.getConfigField().getVocabularyUrl(), ConfigurationHelper.getInstance().getGoobiAuthorityServerUrl(),
                    ConfigurationHelper.getInstance().getGoobiAuthorityServerUrl() + ConfigurationHelper.getInstance().getGoobiAuthorityServerUser()
                    + "/vocabularies/" + selectedVocabularyRecord.getVocabularyId() + "/records/" + selectedVocabularyRecord.getId());
        } else {
            md.setAutorityFile(searchField.getConfigField().getVocabularyUrl(), searchField.getConfigField().getVocabularyUrl(),
                    searchField.getConfigField().getVocabularyUrl() + "/vocabularies/" + selectedVocabularyRecord.getVocabularyId() + "/"
                            + selectedVocabularyRecord.getId());
        }

    }

    /**
     * Search within sources
     * 
     */

    public void searchSource() {
        List<StringPair> data = new ArrayList<>();

        for (String field : configuration.getSourceSearchFields()) {
            data.add(new StringPair(field, searchValue));
        }

        sources = VocabularyManager.findRecords(configuration.getSourceVocabularyName(), data);

        if (sources == null || sources.isEmpty()) {
            showNotHits = true;
        } else {
            showNotHits = false;
        }
        Collections.sort(sources);
    }

    /**
     * Add selected source to the current metadata field
     * 
     * 
     */

    public void addSource() {
        String sourceId = String.valueOf(selectedSource.getId());
        String sourceUri;
        String sourceName = "";
        String sourceType = "";
        String sourceLink = "";

        if (StringUtils.isNotBlank(ConfigurationHelper.getInstance().getGoobiAuthorityServerUser())
                && StringUtils.isNotBlank(ConfigurationHelper.getInstance().getGoobiAuthorityServerUrl())) {
            sourceUri =
                    ConfigurationHelper.getInstance().getGoobiAuthorityServerUrl() + ConfigurationHelper.getInstance().getGoobiAuthorityServerUser()
                    + "/vocabularies/" + selectedSource.getVocabularyId() + "/records/" + selectedSource.getId();
        } else {
            sourceUri = EntityConfig.vocabularyUrl + "/vocabularies/" + selectedSource.getVocabularyId() + "/" + selectedSource.getId();
        }

        sourceName = getSourceFieldValue(configuration.getSourceNameFields());
        sourceType = getSourceFieldValue(configuration.getSourceTypeFields());
        sourceLink = getSourceFieldValue(configuration.getSourceUrlFields());

        SourceField source = currentField.new SourceField(sourceId, sourceUri, sourceName, sourceType, sourceLink, pages);

        MetadataGroup mg = null;

        try {
            mg = new MetadataGroup(prefs.getMetadataGroupTypeByName("Source"));
            Metadata sourceIdMetadata = new Metadata(prefs.getMetadataTypeByName("SourceID"));
            sourceIdMetadata.setValue(sourceId);
            sourceIdMetadata.setAutorityFile(sourceName, sourceUri, sourceLink);
            mg.addMetadata(sourceIdMetadata);

            Metadata sourceNameMetadata = new Metadata(prefs.getMetadataTypeByName("SourceName"));
            sourceNameMetadata.setValue(sourceName);
            mg.addMetadata(sourceNameMetadata);

            Metadata sourceTypeMetadata = new Metadata(prefs.getMetadataTypeByName("SourceType"));
            sourceTypeMetadata.setValue(sourceType);
            mg.addMetadata(sourceTypeMetadata);
            Metadata sourceLinkMetadata = new Metadata(prefs.getMetadataTypeByName("SourceLink"));
            sourceLinkMetadata.setValue(sourceLink);
            mg.addMetadata(sourceLinkMetadata);
            Metadata sourcePageMetadata = new Metadata(prefs.getMetadataTypeByName("SourcePage"));
            sourcePageMetadata.setValue(pages);
            mg.addMetadata(sourcePageMetadata);
        } catch (MetadataTypeNotAllowedException e) {
            log.error(e);
        }

        currentField.addSource(source, mg);
        pages = "";
    }

    private String getSourceFieldValue(List<String> configuredFieldNames) {
        for (String configuredFieldName : configuredFieldNames) {
            String fieldname = null;
            String lang = null;
            if (configuredFieldName.matches(".+\\[\\w+\\]")) {
                fieldname = configuredFieldName.substring(0, configuredFieldName.indexOf("["));
                lang = configuredFieldName.substring(configuredFieldName.indexOf("[") + 1, configuredFieldName.length() - 1);
            } else {
                fieldname = configuredFieldName;
            }
            for (Field field : selectedSource.getFields()) {
                if (StringUtils.isBlank(lang) && StringUtils.isBlank(field.getDefinition().getLanguage())) {
                    if (field.getDefinition().getLabel().equals(fieldname)) {
                        return field.getValue();
                    }
                } else if (StringUtils.isNotBlank(lang) && StringUtils.isNotBlank(field.getDefinition().getLanguage())) {
                    if (field.getDefinition().getLabel().equals(fieldname) && field.getDefinition().getLanguage().equals(lang)) {
                        return field.getValue();
                    }

                }
            }

        }
        return "";
    }

    public void editRelationship(Relationship relationship) {
        selectedBreadcrumb = new BreadcrumbItem(relationship.getEntityName(), "", Integer.valueOf(relationship.getProcessId()), "", "");
        loadSelectedBreadcrumb();
    }

    public void createNewSource() {
        selectedSource = new VocabRecord();
        selectedSource.setVocabularyId(sourceVocabulary.getId());
        List<Field> fieldList = new ArrayList<>();
        for (Definition definition : sourceVocabulary.getStruct()) {
            Field field = new Field(definition.getLabel(), definition.getLanguage(), "", definition);
            fieldList.add(field);
        }
        selectedSource.setFields(fieldList);
    }

    public void saveAndAddSource() {
        VocabularyManager.saveRecord(selectedSource.getVocabularyId(), selectedSource);
        addSource();
    }

    public void addRelationship(EntityType type) {
        selectedEntity = null;
        entitySearch = "";
        entityType = type;
        entities.clear();
        relationshipTypes.clear();

        // get all relationship types for the entity type
        for (RelationshipType rel : entity.getCurrentType().getConfiguredRelations()) {
            if (rel.getDestinationType().equals(type.getName())) {
                relationshipTypes.add(rel);
            }
        }
    }

    public void searchForType(EntityType type) {
        selectedEntity = null;
        entitySearch = "";
        entityType = type;
        entities.clear();
    }

    public void searchWithoutType() {
        selectedEntity = null;
        entitySearch = "";
        entityType = null;
        entities.clear();
    }

    public void addBreadcrumbs() {
        for (Entity e : entities) {
            if (e.isSelected()) {
                boolean isAdded = false;
                for (BreadcrumbItem bce : breadcrumbList) {
                    if (bce.getProcessId() == e.getCurrentProcess().getId().intValue()) {
                        isAdded = true;
                    }

                }
                if (!isAdded) {
                    BreadcrumbItem item = new BreadcrumbItem(e.getCurrentType().getName(), e.getEntityName(), e.getCurrentProcess().getId(),
                            e.getCurrentType().getColor(), e.getCurrentType().getIcon());
                    breadcrumbList.add(item);
                }
            }
        }
        entities.clear();
        selectedEntity = null;
        entitySearch = "";
        entityType = null;
    }

    public void searchEntity() {
        //  search for processes, load metadata from mets file?
        entities.clear();

        //index.EntitySearch
        StringBuilder sql = new StringBuilder();
        sql.append("select m1.processid from metadata m1 ");
        sql.append("left join metadata m2 on m1.processid = m2.processid ");
        sql.append("where  m1.name=\"docstruct\" and m1.value =\"");
        sql.append(entityType.getRulesetName());
        sql.append("\" and m2.name=\"index.EntitySearch\" and m2.value like \"%");
        String searchTerm = entitySearch;
        searchTerm = searchTerm.replace(" ", "%");
        searchTerm = searchTerm.replace("`", "_");
        searchTerm = searchTerm.replace("’", "_");
        searchTerm = searchTerm.replace("\'", "_");
        sql.append(searchTerm);
        sql.append("%\"; ");

        // load mets files, open entities
        List<?> rows = ProcessManager.runSQL(sql.toString());
        for (Object obj : rows) {
            Object[] objArr = (Object[]) obj;
            String processid = (String) objArr[0];
            Process process = ProcessManager.getProcessById(Integer.parseInt(processid));
            Entity e = new Entity(configuration, process);
            entities.add(e);
        }
    }

    public void createEntity() {
        // save + close current entity
        if (entity != null) {
            entity.saveEntity();
        }
        Process template = ProcessManager.getProcessById(configuration.getProcessTemplateId());

        String processname = UUID.randomUUID().toString();

        Fileformat fileformat = null;

        try {
            // create new metadata file with given type
            fileformat = new MetsMods(prefs);
            DigitalDocument dd = new DigitalDocument();
            fileformat.setDigitalDocument(dd);
            DocStruct logical = dd.createDocStruct(prefs.getDocStrctTypeByName(entityType.getRulesetName()));
            dd.setLogicalDocStruct(logical);
            Metadata id = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
            id.setValue(processname);
            logical.addMetadata(id);
            DocStruct physical = dd.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
            dd.setPhysicalDocStruct(physical);
            Metadata mdForPath = new Metadata(prefs.getMetadataTypeByName("pathimagefiles"));
            mdForPath.setValue("file:///");
            physical.addMetadata(mdForPath);
        } catch (UGHException e) {
            log.error(e);
        }

        // create process
        Process newProcess = new BeanHelper().createAndSaveNewProcess(template, processname, fileformat);
        // create and open new entity
        entity = new Entity(configuration, newProcess);
        entity.saveEntity();

        //  breadcrumb
        BreadcrumbItem item = new BreadcrumbItem(entity.getCurrentType().getName(), entity.getEntityName(), entity.getCurrentProcess().getId(),
                entity.getCurrentType().getColor(), entity.getCurrentType().getIcon());
        breadcrumbList.add(item);
        selectedBreadcrumb = item;
    }

    public void setRelationship(String selectedRelationship) {
        if (selectedRelationship == null) {
            this.selectedRelationship = null;
        } else {
            for (RelationshipType r : relationshipTypes) {
                if (selectedRelationship.equals(r.getRelationshipNameEn())) {
                    this.selectedRelationship = r;
                    break;
                }
            }
        }
    }

    public String getRelationship() {
        if (selectedRelationship == null) {
            return null;
        }
        return selectedRelationship.getRelationshipNameEn();
    }

    public void addRelationshipBetweenEntities() {

        entity.addRelationship(selectedEntity, relationshipData, relationshipStartDate, relationshipEndDate, selectedRelationship, false);

        // reverse relationship in other entity
        selectedEntity.addRelationship(entity, relationshipData, relationshipStartDate, relationshipEndDate, selectedRelationship, true);

        // save both entities
        entity.saveEntity();
        selectedEntity.saveEntity();

        entities.clear();
        selectedEntity = null;
        entitySearch = "";
        entityType = null;
    }

    public void removeRelationship(EntityType type, Relationship relationship) {

        entity.getLinkedRelationships().get(type).remove(relationship);

        try {
            entity.getCurrentFileformat().getDigitalDocument().getLogicalDocStruct().removeMetadataGroup(relationship.getMetadataGroup());
        } catch (PreferencesException e) {
            log.error(e);
        }

        entity.saveEntity();

        // load other process, remove relationship from there
        Process otherProcess = ProcessManager.getProcessById(Integer.parseInt(relationship.getProcessId()));
        if (otherProcess == null) {
            return;
        }
        Entity other = new Entity(configuration, otherProcess);
        List<Relationship> relationships = other.getLinkedRelationships().get(entity.getCurrentType());
        String processid = String.valueOf(entity.getCurrentProcess().getId());
        Relationship otherRleationship = null;
        for (Relationship r : relationships) {
            if (r.getProcessId().equals(processid) && r.getVocabularyUrl().equals(relationship.getVocabularyUrl())) {
                otherRleationship = r;
                break;
            }
        }
        if (otherRleationship != null) {
            try {
                relationships.remove(otherRleationship);
                other.getCurrentFileformat().getDigitalDocument().getLogicalDocStruct().removeMetadataGroup(otherRleationship.getMetadataGroup());
            } catch (PreferencesException e) {
                log.error(e);
            }
            other.saveEntity();
        }
    }

    public void saveEntity() {
        entity.saveEntity();
    }

    public void exportSingleRecord() {

        // get current process
        Process p = entity.getCurrentProcess();

        // check export status

        if (!"Published".equals(entity.getStatusProperty().getWert())) {
            // mark process as published
            entity.getStatusProperty().setWert("Published");
        }
        entity.generateBibliography();
        // save
        entity.saveEntity();
        // run export plugin for current process

        IExportPlugin exportPlugin = (IExportPlugin) PluginLoader.getPluginByTitle(PluginType.Export, configuration.getExportPluginName());
        try {
            exportPlugin.startExport(p);
        } catch (DocStructHasNoTypeException | PreferencesException | WriteException | MetadataTypeNotAllowedException | ReadException
                | TypeNotAllowedForParentException | IOException | InterruptedException | ExportFileException | UghHelperException | SwapException
                | DAOException e) {
            log.error(e);
        }
    }

    public void exportAllRecords() {
        // mark process as published
        Process p = entity.getCurrentProcess();

        // check export status
        if (!"Published".equals(entity.getStatusProperty().getWert())) {
            // mark process as published
            entity.getStatusProperty().setWert("Published");
        }
        entity.generateBibliography();
        // save
        entity.saveEntity();
        // run export plugin for current process
        IExportPlugin exportPlugin = (IExportPlugin) PluginLoader.getPluginByTitle(PluginType.Export, configuration.getExportPluginName());
        try {
            exportPlugin.startExport(p);
            // run export for all linked entities with status published
            for (List<Relationship> relationships : entity.getLinkedRelationships().values()) {
                for (Relationship rel : relationships) {
                    if ("Published".equals(rel.getProcessStatus()) && StringUtils.isNotBlank(rel.getProcessId())
                            && StringUtils.isNumeric(rel.getProcessId())) {
                        Process process = ProcessManager.getProcessById(Integer.parseInt(rel.getProcessId()));
                        if (process != null) {
                            exportPlugin.startExport(process);
                        }
                    }
                }
            }

        } catch (DocStructHasNoTypeException | PreferencesException | WriteException | MetadataTypeNotAllowedException | ReadException
                | TypeNotAllowedForParentException | IOException | InterruptedException | ExportFileException | UghHelperException | SwapException
                | DAOException e) {
            log.error(e);
        }
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.FULL;
    }

    public List<EntityType> getAllEntityTypes() {
        return configuration.getAllTypes();
    }

    public void setTypeAsString(String type) {
        if (StringUtils.isNotBlank(type)) {
            for (EntityType et : getAllEntityTypes()) {
                if (et.getName().equals(type)) {
                    entityType = et;
                    break;
                }
            }
        }
    }

    public String getTypeAsString() {
        if (entityType != null) {
            return entityType.getName();
        }
        return "";
    }

    public String deleteEntity() {
        // run through relationships; remove process
        for (Entry<EntityType, List<Relationship>> entry : entity.getLinkedRelationships().entrySet()) {
            List<Relationship> relations = new ArrayList<>(entry.getValue());
            for (Relationship rel : relations) {
                // remove each relationship
                removeRelationship(entry.getKey(), rel);
            }
        }

        // remove entity from breadcrumbs
        for (BreadcrumbItem bci : breadcrumbList) {
            if (bci.getProcessId() == entity.getCurrentProcess().getId().intValue()) {
                breadcrumbList.remove(bci);
                break;
            }
        }

        // remove process from database
        ProcessManager.deleteProcess(entity.getCurrentProcess());

        entity = null;
        return exitPlugin();
        // remove screen?
    }

    public boolean isShowAddTabButton() {

        if (entities != null) {
            for (Entity e : entities) {
                if (e.isSelected()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void updateDisplayName(AjaxBehaviorEvent event) {
        try {
            DocStruct logical = entity.getCurrentFileformat().getDigitalDocument().getLogicalDocStruct();
            entity.generateDisplayName(logical, logical.getType().getName());
            selectedBreadcrumb.setEntityName(entity.getEntityName());
        } catch (PreferencesException e) {
            log.error(e);
        }
    }

    /*
     * SQL statements to generate display name for each type

    clear old data: delete from prozesseeigenschaften where titel = 'DisplayName' and wert is null;


    insert into prozesseeigenschaften (titel,WERT,prozesseID,creationDate)
    select 'DisplayName', title, ProzesseID, '2022-10-11 12:00:00'
    from (select ProzesseID, concat(m1.value, " ", m2.value) as title from (
    select prozesse.ProzesseID, Wert from prozesse left join prozesseeigenschaften on prozesse.ProzesseID = prozesseeigenschaften.prozesseID
    and prozesseeigenschaften.titel = 'DisplayName') t
    left join metadata m1 on t.ProzesseID = m1.processid and m1.name = 'FirstnameEn'
    left join metadata m2 on t.ProzesseID = m2.processid and m2.name = 'LastnameEn'
    left join metadata m3 on t.ProzesseID = m3.processid and m3.name = 'DocStruct'
     where t.WERT is null  and m3.value = 'Person') x;

    insert into prozesseeigenschaften (titel,WERT,prozesseID,creationDate)
    select 'DisplayName', title, ProzesseID, '2022-10-11 12:00:00'
    from (select ProzesseID, m1.value as title from (
    select prozesse.ProzesseID, Wert from prozesse left join prozesseeigenschaften on prozesse.ProzesseID = prozesseeigenschaften.prozesseID
    and prozesseeigenschaften.titel = 'DisplayName') t
    left join metadata m1 on t.ProzesseID = m1.processid and m1.name = 'NameEN'
    left join metadata m3 on t.ProzesseID = m3.processid and m3.name = 'DocStruct'
     where t.WERT is null  and m3.value = 'Event') x;


    insert into prozesseeigenschaften (titel,WERT,prozesseID,creationDate)
    select 'DisplayName', title, ProzesseID, '2022-10-11 12:00:00'
    from (select ProzesseID, m1.value as title from (
    select prozesse.ProzesseID, Wert from prozesse left join prozesseeigenschaften on prozesse.ProzesseID = prozesseeigenschaften.prozesseID
    and prozesseeigenschaften.titel = 'DisplayName') t
    left join metadata m1 on t.ProzesseID = m1.processid and m1.name = 'TitleEN'
    left join metadata m3 on t.ProzesseID = m3.processid and m3.name = 'DocStruct'
     where t.WERT is null  and m3.value = 'Work') x;


    insert into prozesseeigenschaften (titel,WERT,prozesseID,creationDate)
    select 'DisplayName', title, ProzesseID, '2022-10-11 12:00:00'
    from (select ProzesseID, m1.value as title from (
    select prozesse.ProzesseID, Wert from prozesse left join prozesseeigenschaften on prozesse.ProzesseID = prozesseeigenschaften.prozesseID
    and prozesseeigenschaften.titel = 'DisplayName') t
    left join metadata m1 on t.ProzesseID = m1.processid and m1.name = 'NameEN'
    left join metadata m3 on t.ProzesseID = m3.processid and m3.name = 'DocStruct'
     where t.WERT is null  and m3.value = 'Agent') x;

    insert into prozesseeigenschaften (titel,WERT,prozesseID,creationDate)
    select 'DisplayName', title, ProzesseID, '2022-10-11 12:00:00'
    from (select ProzesseID, m1.value as title from (
    select prozesse.ProzesseID, Wert from prozesse left join prozesseeigenschaften on prozesse.ProzesseID = prozesseeigenschaften.prozesseID
    and prozesseeigenschaften.titel = 'DisplayName') t
    left join metadata m1 on t.ProzesseID = m1.processid and m1.name = 'TitleEN'
    left join metadata m3 on t.ProzesseID = m3.processid and m3.name = 'DocStruct'
     where t.WERT is null  and m3.value = 'Award') x;

     */

}
