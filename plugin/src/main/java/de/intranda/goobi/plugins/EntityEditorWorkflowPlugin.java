package de.intranda.goobi.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginType;
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
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.VocabularyManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Metadata;

@PluginImplementation
@Log4j2
public class EntityEditorWorkflowPlugin implements IWorkflowPlugin, IPlugin {

    @Getter
    private String title = "intranda_workflow_entity_editor";

    @Getter
    private PluginType type = PluginType.Workflow;

    @Getter
    private String gui = "/uii/plugin_workflow_entity_editor.xhtml";

    // contains the last opened elements
    @Getter
    private List<BreadcrumbItem> breadcrumbList = new ArrayList<>();

    // select a breadcrumb to open
    @Getter
    @Setter
    private BreadcrumbItem selectedBreadcrumb;

    // configuration object
    private EntityConfig configuration;

    // current field for vocabulary search
    @Getter
    @Setter
    private MetadataField searchField;

    // search value
    @Getter
    @Setter
    private String searchValue;

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
    private MetadataField currentField;
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

    //    @Getter
    //    private Map<EntityType, List<Relationship>> linkedRelationships;

    @Getter
    @Setter
    private String mainScrollPosition;

    @Getter
    @Setter
    private String sourceMode;

    @Getter
    private Entity entity;

    /**
     * Constructor
     */
    public EntityEditorWorkflowPlugin() {
        XMLConfiguration config = ConfigPlugins.getPluginConfig(title);
        config.setExpressionEngine(new XPathExpressionEngine());

        configuration = new EntityConfig(config);

        sourceVocabulary = VocabularyManager.getVocabularyById(configuration.getSourceVocabularyId());

        loadTestdata();
    }

    /**
     * generate some test data - remove it once we have a real entry
     * 
     */
    public void loadTestdata() {

        // load testdata
        Process currentProcess = ProcessManager.getProcessByExactTitle("SamplePerson");
        entity = new Entity(configuration, currentProcess);
        //            prefs = currentProcess.getRegelsatz().getPreferences();
        //
        //            currentFileformat = new MetsMods(prefs);
        //            currentFileformat.read(currentProcess.getMetadataFilePath());
        //
        //            readMetadata();

        BreadcrumbItem root = new BreadcrumbItem("Dashboard", "Dashboard", 0, "#ccc", null);
        breadcrumbList.add(root);

        BreadcrumbItem item = new BreadcrumbItem("Person", "John Doe", 7, "#df07b9", "fa-user");
        breadcrumbList.add(item);

        BreadcrumbItem item2 = new BreadcrumbItem("Award", "Darwin Award", 11, "#05b8cd", "fa-trophy");
        breadcrumbList.add(item2);

        BreadcrumbItem item3 = new BreadcrumbItem("Work", "Mona Lisa", 10, "#900688", "fa-picture-o");
        breadcrumbList.add(item3);

        BreadcrumbItem item4 = new BreadcrumbItem("Event", "FIFA World Cup", 9, "#19b609", "fa-calendar");
        breadcrumbList.add(item4);

        BreadcrumbItem item5 = new BreadcrumbItem("Agent", "intranda", 8, "#e81c0c", "fa-university");
        breadcrumbList.add(item5);

    }

    /**
     * Close the current element and open the selected breadcrumb
     * 
     * {@link selectedBreadcrumb} must be set first
     * 
     * @return
     */

    public String loadSelectedBreadcrumb() {
        // TODO save current entity?

        //  check if dashboard was selected -> exit to start page
        if (0 == selectedBreadcrumb.getProcessId() && "Dashboard".equals(selectedBreadcrumb.getEntityName())) {
            return "/uii/index.xhtml";
        }

        // create BreadcrumbItem for the current object
        BreadcrumbItem item = new BreadcrumbItem(entity.getCurrentType().getName(), entity.getEntityName(), entity.getCurrentProcess().getId(),
                entity.getCurrentType().getColor(), entity.getCurrentType().getIcon());
        breadcrumbList.add(item);

        // load selected data

        Process currentProcess = ProcessManager.getProcessById(selectedBreadcrumb.getProcessId());
        entity = new Entity(configuration, currentProcess);

        return "";
    }

    /**
     * Search within a vocabulary
     * 
     * {@link searchField} must be set first
     */

    public void searchVocabulary() {

        List<StringPair> data = new ArrayList<>();

        for (String field : searchField.getConfigField().getSearchFields()) {
            data.add(new StringPair(field, searchValue));
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
            sourceUri = configuration.vocabularyUrl + "/vocabularies/" + selectedSource.getVocabularyId() + "/" + selectedSource.getId();
        }

        sourceName = getSourceFieldValue(configuration.getSourceNameFields());
        sourceType = getSourceFieldValue(configuration.getSourceTypeFields());
        sourceLink = getSourceFieldValue(configuration.getSourceUrlFields());

        //  pages;
        SourceField source = currentField.new SourceField(sourceId, sourceUri, sourceName, sourceType, sourceLink, pages);
        currentField.addSource(source);
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

    // button to generate bibliography

    @Getter
    @Setter
    private String entitySearch;

    @Getter
    private List<Entity> entities = new ArrayList<>();

    @Getter
    private EntityType entityType;

    @Getter
    private List<RelationshipType> relationshipTypes = new ArrayList<>();

    @Getter
    @Setter
    private Entity selectedEntity;
    @Getter
    @Setter
    private RelationshipType selectedRelationship;

    @Getter
    @Setter
    private String relationshipStartDate;

    @Getter
    @Setter
    private String relationshipEndDate;

    @Getter
    @Setter
    private String relationshipData;

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

    public void searchEntity() {
        //  search for processes, load metadata from mets file?
        String sql = "select processid from metadata where name=\"index." + entityType.getName() + "Search\" and value like \"%"
                + StringEscapeUtils.escapeSql(entitySearch) + "%\"";
        // TODO exclude current entity
        // TODO add paginator? limit to 10?

        // load mets files, open entities
        List<?> rows = ProcessManager.runSQL(sql);
        for (Object obj : rows) {
            Object[] objArr = (Object[]) obj;
            String processid = (String) objArr[0];
            Process process = ProcessManager.getProcessById(Integer.parseInt(processid));
            Entity e = new Entity(configuration, process);
            entities.add(e);
        }

    }

    public void createEntity(EntityType type) {
        // TODO save + close current entity
        // TODO create breadcrumb
        // TODO create new entity with given type

    }

    public void saveEntity() {
        // TODO save metadata
    }

    // TODO which language?
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
        List<Relationship> relationships = entity.getLinkedRelationships().get(selectedEntity.getCurrentType());

        Relationship rel = new Relationship();
        rel.setAdditionalData(relationshipData);
        rel.setBeginningDate(relationshipStartDate);
        rel.setEndDate(relationshipEndDate);
        rel.setDisplayName(selectedEntity.getEntityName());
        rel.setEntityName(selectedEntity.getCurrentType().getName());
        rel.setProcessId(String.valueOf(selectedEntity.getCurrentProcess().getId()));
        rel.setProcessStatus("TODO");
        rel.setType(selectedRelationship.getRelationshipNameEn());
        rel.setVocabularyName(selectedRelationship.getVocabularyName());
        rel.setVocabularyUrl(selectedRelationship.getVocabularyUrl());
        relationships.add(rel);

        // reverse relationship in other

        relationships = selectedEntity.getLinkedRelationships().get(entity.getCurrentType());
        Relationship reverse = new Relationship();
        reverse.setAdditionalData(relationshipData);
        reverse.setBeginningDate(relationshipStartDate);
        reverse.setEndDate(relationshipEndDate);
        reverse.setDisplayName(entity.getEntityName());
        reverse.setEntityName(entity.getCurrentType().getName());
        reverse.setProcessId(String.valueOf(entity.getCurrentProcess().getId()));
        reverse.setProcessStatus("TODO");
        if (StringUtils.isNotBlank(selectedRelationship.getReversedRelationshipNameEn())) {
            reverse.setType(selectedRelationship.getReversedRelationshipNameEn());
        } else {
            reverse.setType(selectedRelationship.getRelationshipNameEn());
        }
        reverse.setVocabularyName(selectedRelationship.getVocabularyName());
        reverse.setVocabularyUrl(selectedRelationship.getVocabularyUrl());
        relationships.add(reverse);
        // TODO save both entities
    }

}
