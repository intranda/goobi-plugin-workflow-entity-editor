package de.intranda.goobi.plugins;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.faces.event.AjaxBehaviorEvent;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.geonames.Style;
import org.geonames.Toponym;
import org.geonames.ToponymSearchCriteria;
import org.geonames.ToponymSearchResult;
import org.geonames.WebService;
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
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.exceptions.UghHelperException;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.VocabularyManager;
import io.goobi.workflow.locking.LockingBean;
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

    // search value for geonames
    @Getter
    @Setter
    private String geonamesSearchValue;

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
    private String sourceType = "";

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

    @Getter
    private transient List<Toponym> resultList;
    @Getter
    @Setter
    private transient Toponym currentToponym;
    @Getter
    private int totalResults;

    @Getter
    @Setter
    private String imageUrl;
    @Getter
    @Setter
    private String imageName;

    @Getter
    private transient Entity changeRelationshipEntity;
    private transient Relationship changeRelationship;

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
            // unlock current entity
            LockingBean.freeObject(String.valueOf(entity.getCurrentProcess().getId()));
        }
        //  check if dashboard was selected -> exit to start page
        if (0 == selectedBreadcrumb.getProcessId() && "Dashboard".equals(selectedBreadcrumb.getEntityName())) {
            return exitPlugin();
        }
        // load selected data

        Process currentProcess = ProcessManager.getProcessById(selectedBreadcrumb.getProcessId());
        prefs = currentProcess.getRegelsatz().getPreferences();

        if (!LockingBean.lockObject(String.valueOf(currentProcess.getId()), Helper.getCurrentUser().getNachVorname())) {
            Helper.setFehlerMeldung("plugin_workflow_entity_locked");
            return "";
        }

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

    public String removeBreadcrumb(BreadcrumbItem breadcrumb) {
        breadcrumbList.remove(breadcrumb);
        if (breadcrumbList.isEmpty()) {
            return exitPlugin();
        }
        return "";
    }

    public String exitPlugin() {
        // unlock current object
        if (entity != null) {
            LockingBean.freeObject(String.valueOf(entity.getCurrentProcess().getId()));
        }
        return "/uii/index.xhtml";
    }

    /**
     * Search within a vocabulary
     * 
     * {@link searchField} must be set first
     */

    public void searchVocabulary() {
        if (entity != null) {
            LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));
        }
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
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

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

    public void searchGeonames() {
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));
        String credentials = ConfigurationHelper.getInstance().getGeonamesCredentials();
        WebService.setUserName(credentials);
        ToponymSearchCriteria searchCriteria = new ToponymSearchCriteria();
        searchCriteria.setNameEquals(geonamesSearchValue);
        searchCriteria.setStyle(Style.FULL);

        Set<String> languageCodes = new HashSet<>();
        for (String field : searchField.getConfigField().getSearchFields()) {
            languageCodes.add(field);
        }
        searchCriteria.setCountryCodes(languageCodes);
        try {
            ToponymSearchResult searchResult = null;
            if (ConfigurationHelper.getInstance().isUseProxy()) {
                String proxyUrl = ConfigurationHelper.getInstance().getProxyUrl();
                int port = ConfigurationHelper.getInstance().getProxyPort();
                SocketAddress addr = new InetSocketAddress(proxyUrl, port);
                Proxy proxy = new Proxy(Type.HTTP, addr);
                WebService.setProxy(proxy);
            }
            searchResult = WebService.search(searchCriteria);
            resultList = searchResult.getToponyms();
            totalResults = searchResult.getTotalResultsCount();
        } catch (Exception e) {
            log.error(e);
        }

        if (totalResults == 0) {
            showNotHits = true;
        } else {
            showNotHits = false;
        }
    }

    public void importGeonamesData() {
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

        Metadata md = searchField.getMetadata();
        md.setValue(currentToponym.getName());
        md.setAutorityFile("geonames", "http://www.geonames.org/", "" + currentToponym.getGeoNameId());
        resultList = new ArrayList<>();
    }

    /**
     * Search within sources
     * 
     */

    public void searchSource() {
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

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
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

        String sourceId = String.valueOf(selectedSource.getId());
        String sourceUri;
        String sourceName = "";
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
                } else if (StringUtils.isNotBlank(lang) && StringUtils.isNotBlank(field.getDefinition().getLanguage())
                        && field.getDefinition().getLabel().equals(fieldname) && field.getDefinition().getLanguage().equals(lang)) {
                    return field.getValue();
                }
            }
        }
        return "";
    }

    public void editRelationship(Relationship relationship) {
        selectedBreadcrumb = new BreadcrumbItem(relationship.getEntityName(), "", Integer.parseInt(relationship.getProcessId()), "", "");
        loadSelectedBreadcrumb();
    }

    public void createNewSource() {
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));
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
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

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
        if (entity != null) {
            LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));
        }
        selectedEntity = null;
        entitySearch = "";
        entityType = type;
        entities.clear();
    }

    public void searchWithoutType() {
        if (entity != null) {
            LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));
        }
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
        if (entity == null && !breadcrumbList.isEmpty()) {
            selectedBreadcrumb = breadcrumbList.get(0);
            loadSelectedBreadcrumb();
        }

    }

    public void searchEntity() {
        if (entity != null) {
            LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));
        }
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
            try {
                Entity e = new Entity(configuration, process);
                entities.add(e);
            } catch (IllegalArgumentException e) {
                // ignore this error, it is already logged
            }
        }
    }

    public void createEntity() {
        // save + close current entity
        if (entity != null) {
            entity.saveEntity();
            // unlock
            LockingBean.freeObject(String.valueOf(entity.getCurrentProcess().getId()));
        }
        Process template = ProcessManager.getProcessById(configuration.getProcessTemplateId());
        prefs = template.getRegelsatz().getPreferences();
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
        entity.getDisplayNameProperty().setWert(processname);
        entity.saveEntity();
        LockingBean.lockObject(String.valueOf(entity.getCurrentProcess().getId()), Helper.getCurrentUser().getNachVorname());

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

    public void changeRelationship(Relationship relationship) {
        changeRelationship = relationship;
        // try to load other entity
        Process currentProcess = ProcessManager.getProcessById(Integer.parseInt(relationship.getProcessId()));
        prefs = currentProcess.getRegelsatz().getPreferences();

        if (!LockingBean.lockObject(String.valueOf(currentProcess.getId()), Helper.getCurrentUser().getNachVorname())) {
            Helper.setFehlerMeldung("plugin_workflow_entity_locked");
            changeRelationshipEntity = null;
            return;
        }
        relationshipStartDate = relationship.getBeginningDate();
        relationshipEndDate = relationship.getEndDate();
        relationshipData = relationship.getAdditionalData();
        changeRelationshipEntity = new Entity(configuration, currentProcess);
        addRelationship(changeRelationshipEntity.getCurrentType());
        setRelationship(relationship.getType().getRelationshipNameEn());
    }

    public void changeRelationshipBetweenEntities() {
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

        if (LockingBean.isLocked(String.valueOf(changeRelationshipEntity.getCurrentProcess().getId())) && !LockingBean
                .lockObject(String.valueOf(changeRelationshipEntity.getCurrentProcess().getId()), Helper.getCurrentUser().getNachVorname())) {
            Helper.setFehlerMeldung("plugin_workflow_entity_locked");
            return;
        }

        // find reverse relationship
        List<Relationship> relationships = changeRelationshipEntity.getLinkedRelationships().get(entity.getCurrentType());
        String processid = String.valueOf(entity.getCurrentProcess().getId());
        Relationship otherRelationship = null;
        for (Relationship r : relationships) {
            if (r.getProcessId().equals(processid) && r.getVocabularyUrl().equals(changeRelationship.getVocabularyUrl())) {
                otherRelationship = r;
                break;
            }
        }
        if (otherRelationship != null) {
            // update other type
            otherRelationship.setType(selectedRelationship);
            if (selectedRelationship.isDisplayAdditionalData()) {
                otherRelationship.setAdditionalData(relationshipData);
            } else {
                otherRelationship.setAdditionalData(null);
            }
            if (selectedRelationship.isDisplayStartDate()) {
                otherRelationship.setBeginningDate(relationshipStartDate);
            } else {
                otherRelationship.setBeginningDate(null);
            }
            if (selectedRelationship.isDisplayEndDate()) {
                otherRelationship.setEndDate(relationshipEndDate);
            } else {
                otherRelationship.setEndDate(null);
            }

            // save other process
            changeRelationshipEntity.saveEntity();
        }

        // change relationship in current entity
        changeRelationship.setType(selectedRelationship);
        if (selectedRelationship.isDisplayAdditionalData()) {
            changeRelationship.setAdditionalData(relationshipData);
        } else {
            changeRelationship.setAdditionalData(null);
        }
        if (selectedRelationship.isDisplayStartDate()) {
            changeRelationship.setBeginningDate(relationshipStartDate);
        } else {
            changeRelationship.setBeginningDate(null);
        }
        if (selectedRelationship.isDisplayEndDate()) {
            changeRelationship.setEndDate(relationshipEndDate);
        } else {
            changeRelationship.setEndDate(null);
        }
        // save current entity
        entity.saveEntity();
        freeRelationshipLock();
    }

    public void freeRelationshipLock() {
        if (changeRelationshipEntity != null) {
            LockingBean.freeObject(String.valueOf(changeRelationshipEntity.getCurrentProcess().getId()));
        }
    }

    public void addRelationshipBetweenEntities() {
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

        if (LockingBean.isLocked(String.valueOf(selectedEntity.getCurrentProcess().getId()))
                && !LockingBean.lockObject(String.valueOf(selectedEntity.getCurrentProcess().getId()), Helper.getCurrentUser().getNachVorname())) {
            Helper.setFehlerMeldung("plugin_workflow_entity_locked");
            return;
        }

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
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

        // load other process, remove relationship from there
        Process otherProcess = ProcessManager.getProcessById(Integer.parseInt(relationship.getProcessId()));
        if (otherProcess == null) {
            return;
        }
        // check if relation is locked
        // if this is the case, check if it is locked by the current user
        if (LockingBean.isLocked(String.valueOf(otherProcess.getId()))
                && !LockingBean.lockObject(String.valueOf(otherProcess.getId()), Helper.getCurrentUser().getNachVorname())) {
            Helper.setFehlerMeldung("plugin_workflow_entity_locked");
            return;
        }

        entity.getLinkedRelationships().get(type).remove(relationship);

        try {
            entity.getCurrentFileformat().getDigitalDocument().getLogicalDocStruct().removeMetadataGroup(relationship.getMetadataGroup());
        } catch (PreferencesException e) {
            log.error(e);
        }

        entity.saveEntity();

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
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

        entity.saveEntity();
    }

    public void exportSingleRecord() {
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

        // get current process
        Process p = entity.getCurrentProcess();

        // check export status

        if (!"Published".equals(entity.getStatusProperty().getWert())) {
            // mark process as published
            entity.getStatusProperty().setWert("Published");
        }
        //entity.generateBibliography();
        // save
        entity.saveEntity();
        // run export plugin for current process

        IExportPlugin exportPlugin = (IExportPlugin) PluginLoader.getPluginByTitle(PluginType.Export, configuration.getExportPluginName());
        try {
            exportPlugin.setExportImages(true);
            exportPlugin.startExport(p);
        } catch (DocStructHasNoTypeException | PreferencesException | WriteException | MetadataTypeNotAllowedException | ReadException
                | TypeNotAllowedForParentException | IOException | InterruptedException | ExportFileException | UghHelperException | SwapException
                | DAOException e) {
            log.error(e);
            return;
        }
        Helper.setMeldung("ExportFinished");
    }

    public void exportAllRecords() {
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

        // mark process as published
        Process p = entity.getCurrentProcess();

        // check export status
        if (!"Published".equals(entity.getStatusProperty().getWert())) {
            // mark process as published
            entity.getStatusProperty().setWert("Published");
        }
        // save
        entity.saveEntity();
        // run export plugin for current process
        IExportPlugin exportPlugin = (IExportPlugin) PluginLoader.getPluginByTitle(PluginType.Export, configuration.getExportPluginName());
        try {
            exportPlugin.setExportImages(true);
            exportPlugin.startExport(p);
            // run export for all linked entities with status published
            for (List<Relationship> relationships : entity.getLinkedRelationships().values()) {
                for (Relationship rel : relationships) {
                    if (StringUtils.isNotBlank(rel.getProcessId()) && StringUtils.isNumeric(rel.getProcessId())) {
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
        Helper.setMeldung("ExportFinished");
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
        for (Entry<EntityType, List<Relationship>> entry : entity.getLinkedRelationships().entrySet()) {
            List<Relationship> relations = new ArrayList<>(entry.getValue());
            for (Relationship rel : relations) {
                // check if relation is locked
                // if this is the case, check if it is locked by the current user
                if (LockingBean.isLocked(rel.getProcessId())
                        && !LockingBean.lockObject(rel.getProcessId(), Helper.getCurrentUser().getNachVorname())) {
                    Helper.setFehlerMeldung("plugin_workflow_entity_locked");
                    return "";
                }
            }
        }

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
        LockingBean.updateLocking(String.valueOf(entity.getCurrentProcess().getId()));

        try {
            DocStruct logical = entity.getCurrentFileformat().getDigitalDocument().getLogicalDocStruct();
            entity.generateDisplayName(logical, logical.getType().getName());
            selectedBreadcrumb.setEntityName(entity.getEntityName());
        } catch (PreferencesException e) {
            log.error(e);
        }
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
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
