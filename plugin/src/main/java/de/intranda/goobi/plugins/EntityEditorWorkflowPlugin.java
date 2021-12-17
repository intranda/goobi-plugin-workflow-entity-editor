package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import de.intranda.goobi.plugins.model.ConfiguredField;
import de.intranda.goobi.plugins.model.EntityConfig;
import de.intranda.goobi.plugins.model.EntityConfig.EntityType;
import de.intranda.goobi.plugins.model.MetadataField;
import de.intranda.goobi.plugins.model.MetadataField.SourceField;
import de.intranda.goobi.plugins.model.Relationship;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.VocabularyManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataGroupType;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j2
public class EntityEditorWorkflowPlugin implements IWorkflowPlugin, IPlugin {

    @Getter
    private String title = "intranda_workflow_entity_editor";

    @Getter
    private PluginType type = PluginType.Workflow;

    @Getter
    private String gui = "/uii/plugin_workflow_entity_editor.xhtml";

    // store the current object
    @Getter
    private Process currentProcess;
    @Getter
    private Fileformat currentFileformat;
    @Getter
    private Prefs prefs;

    // entity type of the current object
    @Getter
    private EntityType currentType;

    // contains the last opened elements
    @Getter
    private List<BreadcrumbItem> breadcrumbList = new ArrayList<>();

    // select a breadcrumb to open
    @Getter
    @Setter
    private BreadcrumbItem selectedBreadcrumb;

    // configuration object
    private EntityConfig configuration;

    // contains the display name for the current entity
    @Getter
    private String entityName;

    // list of all metadata fields
    @Getter
    private List<ConfiguredField> metadataFieldList = new ArrayList<>();

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

    @Getter
    private Map<EntityType, List<Relationship>> linkedRelationships;

    @Getter
    @Setter
    private String mainScrollPosition;

    @Getter
    @Setter
    private String sourceMode;

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
        try {
            currentProcess = ProcessManager.getProcessByExactTitle("SamplePerson");
            prefs = currentProcess.getRegelsatz().getPreferences();

            currentFileformat = new MetsMods(prefs);
            currentFileformat.read(currentProcess.getMetadataFilePath());

            readMetadata();

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

        } catch (UGHException | IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }
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
        BreadcrumbItem item =
                new BreadcrumbItem(currentType.getName(), entityName, currentProcess.getId(), currentType.getColor(), currentType.getIcon());
        breadcrumbList.add(item);

        // load selected data
        try {
            currentProcess = ProcessManager.getProcessById(selectedBreadcrumb.getProcessId());
            prefs = currentProcess.getRegelsatz().getPreferences();
            currentFileformat = new MetsMods(prefs);
            currentFileformat.read(currentProcess.getMetadataFilePath());
        } catch (UGHException | IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }
        readMetadata();
        return "";
    }

    // read metadata from fileformat and initilize fields for UI
    private void readMetadata() {
        try {
            DocStruct logical = currentFileformat.getDigitalDocument().getLogicalDocStruct();
            String entityType = logical.getType().getName();
            currentType = configuration.getTypeByName(entityType);
            getDisplayName(logical, entityType);

            metadataFieldList.clear();

            for (ConfiguredField mf : new ArrayList<>(currentType.getConfiguredFields())) {
                mf.clearMetadata();
                if (mf.isGroup()) {
                    MetadataGroupType mgt = prefs.getMetadataGroupTypeByName(mf.getMetadataName());
                    List<MetadataGroup> groups = logical.getAllMetadataGroupsByType(mgt);
                    if (groups.isEmpty()) {
                        // create new group, metadata in it
                        MetadataGroup group = new MetadataGroup(mgt);
                        logical.addMetadataGroup(group);
                        MetadataField field = new MetadataField();
                        field.setConfigField(mf);
                        field.setGroup(group);
                        mf.adMetadataField(field);
                        metadataFieldList.add(mf);
                        for (ConfiguredField subfield : mf.getSubfieldList()) {
                            MetadataType metadataType = prefs.getMetadataTypeByName(subfield.getMetadataName());
                            Metadata metadata = new Metadata(metadataType);
                            group.addMetadata(metadata);
                            MetadataField sub = new MetadataField();
                            sub.setConfigField(subfield);
                            sub.setMetadata(metadata);
                            subfield.adMetadataField(sub);
                            field.addSubField(sub);
                        }
                    } else {
                        for (MetadataGroup group : groups) {
                            MetadataField field = new MetadataField();
                            field.setConfigField(mf);
                            field.setGroup(group);
                            mf.adMetadataField(field);
                            mf.setShowField(true);
                            metadataFieldList.add(mf);
                            for (ConfiguredField subfield : mf.getSubfieldList()) {
                                if ("source".equals(subfield.getFieldType())) {
                                    // Sources
                                    field.setAllowSources(true);
                                    List<MetadataGroup> sources = group.getAllMetadataGroupsByName("Source");
                                    if (!sources.isEmpty()) {
                                        for (MetadataGroup sourceGroup : sources) {
                                            String sourceId = null;
                                            String sourceUri = null;
                                            String sourceName = null;
                                            String sourceType = null;
                                            String sourceLink = null;
                                            String sourcePageRange = null;

                                            for (Metadata md : sourceGroup.getMetadataList()) {
                                                if (md.getType().getName().equals("SourceID")) {
                                                    sourceId = md.getValue();
                                                    sourceUri = md.getAuthorityURI();
                                                } else if (md.getType().getName().equals("SourceName")) {
                                                    sourceName = md.getValue();
                                                } else if (md.getType().getName().equals("SourceType")) {
                                                    sourceType = md.getValue();
                                                } else if (md.getType().getName().equals("SourceLink")) {
                                                    sourceLink = md.getValue();
                                                } else if (md.getType().getName().equals("SourcePage")) {
                                                    sourcePageRange = md.getValue();
                                                }

                                            }
                                            SourceField source =
                                                    field.new SourceField(sourceId, sourceUri, sourceName, sourceType, sourceLink, sourcePageRange);
                                            field.addSource(source);
                                        }
                                    }

                                } else {
                                    MetadataType metadataType = prefs.getMetadataTypeByName(subfield.getMetadataName());
                                    List<Metadata> mdl = group.getMetadataByType(subfield.getMetadataName());
                                    if (mdl.isEmpty()) {
                                        // create new metadata
                                        Metadata metadata = new Metadata(metadataType);
                                        group.addMetadata(metadata);
                                        MetadataField sub = new MetadataField();
                                        sub.setConfigField(subfield);
                                        sub.setMetadata(metadata);
                                        subfield.adMetadataField(sub);
                                        field.addSubField(sub);
                                        // generate metadata value
                                        if ("generated".equals(subfield.getFieldType())) {
                                            String value = subfield.getGenerationRule();
                                            if (StringUtils.isNotBlank(value)) {
                                                VariableReplacer replacer =
                                                        new VariableReplacer(currentFileformat.getDigitalDocument(), prefs, currentProcess, null);
                                                metadata.setValue(replacer.replace(value));
                                                mf.setShowField(true);
                                            }
                                        }

                                    } else {
                                        // merge metadata
                                        for (Metadata metadata : mdl) {
                                            MetadataField sub = new MetadataField();
                                            sub.setConfigField(subfield);
                                            sub.setMetadata(metadata);
                                            subfield.adMetadataField(sub);
                                            field.addSubField(sub);

                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    MetadataType metadataType = prefs.getMetadataTypeByName(mf.getMetadataName());
                    List<? extends Metadata> mdl = logical.getAllMetadataByType(metadataType);
                    if (mdl.isEmpty()) {
                        // create new metadata
                        Metadata metadata = new Metadata(metadataType);
                        logical.addMetadata(metadata);
                        MetadataField field = new MetadataField();
                        field.setConfigField(mf);
                        field.setMetadata(metadata);
                        mf.adMetadataField(field);
                        metadataFieldList.add(mf);
                        // generate metadata value
                        if ("generated".equals(mf.getFieldType())) {
                            String value = mf.getGenerationRule();
                            if (StringUtils.isNotBlank(value)) {
                                VariableReplacer replacer = new VariableReplacer(currentFileformat.getDigitalDocument(), prefs, currentProcess, null);
                                metadata.setValue(replacer.replace(value));
                                mf.setShowField(true);
                            }
                        }
                    } else {
                        // merge metadata
                        for (Metadata metadata : mdl) {
                            MetadataField field = new MetadataField();
                            field.setConfigField(mf);
                            field.setMetadata(metadata);
                            mf.adMetadataField(field);
                            mf.setShowField(true);
                            metadataFieldList.add(mf);
                        }
                    }
                }

                // load relations to other entities
                if (StringUtils.isNotBlank(configuration.getRelationshipMetadataName())) {
                    linkedRelationships = new LinkedHashMap<>();
                    for (EntityType et : configuration.getAllTypes()) {
                        linkedRelationships.put(et, new ArrayList<>());
                    }
                    List<MetadataGroup> relations =
                            logical.getAllMetadataGroupsByType(prefs.getMetadataGroupTypeByName(configuration.getRelationshipMetadataName()));

                    for (MetadataGroup group : relations) {
                        String entity = null;
                        String beginningDate = null;
                        String endDate = null;
                        String additionalData = null;
                        String processId = null;
                        String displayName = null;
                        String type = null;
                        String vocabularyName = null;
                        String vocabularyUrl = null;

                        for (Metadata md : group.getMetadataList()) {
                            String metadataType = md.getType().getName();
                            if (metadataType.equals(configuration.getRelationshipEntityType())) {
                                entity = md.getValue();
                            } else if (metadataType.equals(configuration.getRelationshipBeginningDate())) {
                                beginningDate = md.getValue();
                            } else if (metadataType.equals(configuration.getRelationshipEndDate())) {
                                endDate = md.getValue();
                            } else if (metadataType.equals(configuration.getRelationshipAdditionalData())) {
                                additionalData = md.getValue();
                            } else if (metadataType.equals(configuration.getRelationshipProcessId())) {
                                processId = md.getValue();
                            } else if (metadataType.equals(configuration.getRelationshipDisplayName())) {
                                displayName = md.getValue();
                            } else if (metadataType.equals(configuration.getRelationshipType())) {
                                type = md.getValue();
                                vocabularyName = md.getAuthorityID();
                                vocabularyUrl = md.getAuthorityValue();
                            }

                        }

                        Relationship relationship = new Relationship();
                        relationship.setEntityName(entity);
                        relationship.setBeginningDate(beginningDate);
                        relationship.setEndDate(endDate);
                        relationship.setAdditionalData(additionalData);
                        relationship.setProcessId(processId);
                        relationship.setDisplayName(displayName);
                        relationship.setType(type);
                        relationship.setVocabularyName(vocabularyName);
                        relationship.setVocabularyUrl(vocabularyUrl);

                        for (EntityType et : linkedRelationships.keySet()) {
                            if (et.getName().equals(relationship.getEntityName())) {
                                linkedRelationships.get(et).add(relationship);
                            }
                        }
                    }

                }

            }

        } catch (UGHException e) {
            log.error(e);
        }

    }

    // generate the display name for the current entity
    private void getDisplayName(DocStruct logical, String entityType) {
        // read main name from metadata
        StringBuilder sb = new StringBuilder();
        for (String metadata : currentType.getIdentifyingMetadata().split(" ")) {
            if (metadata.contains("/")) {
                String[] parts = metadata.split("/");
                // first part -> group name
                for (MetadataGroup mg : logical.getAllMetadataGroups()) {
                    if (mg.getType().getName().equals(parts[0])) {
                        // last part metadata name
                        for (Metadata md : mg.getMetadataList()) {
                            if (md.getType().getName().equals(parts[1])) {
                                if (sb.length() > 0) {
                                    sb.append(" ");
                                }
                                sb.append(md.getValue());
                            }
                        }
                    }
                }
            } else {
                for (Metadata md : logical.getAllMetadata()) {
                    if (md.getType().getName().equals(metadata)) {
                        if (sb.length() > 0) {
                            sb.append(" ");
                        }
                        sb.append(md.getValue());
                    }
                }
            }
        }
        entityName = sb.toString();
        if (StringUtils.isBlank(entityName)) {
            entityName = entityType;
        }
    }

    /**
     * Duplicate the selected metadata
     */
    public void duplicateMetadata(ConfiguredField field) {

        if (field.getMetadataList() == null) {
            field.setMetadataList(new ArrayList<>());
        }
        if (field.isGroup()) {
            try {

                MetadataGroup group = new MetadataGroup(prefs.getMetadataGroupTypeByName(field.getMetadataName()));
                currentFileformat.getDigitalDocument().getLogicalDocStruct().addMetadataGroup(group);
                MetadataField f = new MetadataField();
                f.setConfigField(field);
                f.setGroup(group);
                field.adMetadataField(f);
                for (ConfiguredField subfield : field.getSubfieldList()) {
                    if (!subfield.isGroup()) {
                        Metadata otherMetadata = subfield.getMetadataList().get(0).getMetadata();
                        MetadataType metadataType = otherMetadata.getType();
                        Metadata metadata = new Metadata(metadataType);
                        group.addMetadata(metadata);
                        MetadataField sub = new MetadataField();
                        sub.setConfigField(subfield);
                        sub.setMetadata(metadata);
                        f.addSubField(sub);
                    }
                }
            } catch (UGHException e) {
                log.error(e);
            }
        } else {
            try {
                Metadata metadata = new Metadata(prefs.getMetadataTypeByName(field.getMetadataName()));
                currentFileformat.getDigitalDocument().getLogicalDocStruct().addMetadata(metadata);
                MetadataField f = new MetadataField();
                f.setConfigField(field);
                f.setMetadata(metadata);
                field.adMetadataField(f);
            } catch (UGHException e) {
                log.error(e);
            }
        }
        field.setShowField(true);
    }

    /**
     * Delete the selected metadata
     * 
     * @param field
     */

    public void removeMetadata(MetadataField field) {
        ConfiguredField cf = field.getConfigField();
        if (cf.isGroup()) {
            MetadataGroup grp = field.getGroup();
            grp.getParent().removeMetadataGroup(grp, true);
        } else {
            Metadata md = field.getMetadata();
            md.getParent().removeMetadata(md, true);
        }
        cf.getMetadataList().remove(field);

        if (cf.getMetadataList().isEmpty()) {
            duplicateMetadata(cf);
            cf.setShowField(false);
            // generate empty field

        }
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

    public void removeRelationship(EntityType type, Relationship relationship) {

        linkedRelationships.get(type).remove(relationship);
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

    public void generateBibliography() {
        List<SourceField> allSources = new ArrayList<>();
        ConfiguredField bibliography = null;
        for (ConfiguredField cf : metadataFieldList) {
            for (MetadataField mf : cf.getMetadataList()) {
                List<SourceField> sourceFields = mf.getSources();
                for (SourceField s : sourceFields) {
                    if (!allSources.contains(s)) {
                        allSources.add(s);
                    }
                }
            }
            if (cf.getMetadataName().equals("Bibliography")) {
                bibliography = cf;
            }
        }

        // create bibliography from exported sources
        for (SourceField currentSource : allSources) {
            boolean sourceMatched = false;
            String sourceId = currentSource.getSourceId();

            for (MetadataField mf : bibliography.getMetadataList()) {
                for (Metadata md : mf.getGroup().getMetadataList()) {
                    if (md.getType().getName().equals("SourceID") && sourceId.equals(md.getValue())) {
                        sourceMatched = true;
                        break;
                    }
                }
            }

            if (!sourceMatched) {
                // create new field
                bibliography.setShowField(true);
                // if filled, create new entry
                if (bibliography.isFilled()) {
                    bibliography.addValue();
                }
                // get created field
                MetadataField field = bibliography.getMetadataList().get(bibliography.getMetadataList().size() - 1);

                for (MetadataField subfield : field.getSubfields()) {
                    if (subfield.getConfigField().getLabel().equals("Citation")) {
                        subfield.getMetadata().setValue(currentSource.getSourceName());
                        // TODO distinct between source type and bibliography type
                    } else if (subfield.getConfigField().getLabel().equals("Type")) {
                        subfield.getMetadata().setValue(currentSource.getSourceType());
                    } else if (subfield.getConfigField().getLabel().equals("Link")) {
                        subfield.getMetadata().setValue(currentSource.getSourceLink());
                    } else if (subfield.getConfigField().getLabel().equals("SourceID")) {
                        Metadata md = subfield.getMetadata();
                        md.setValue(currentSource.getSourceId());
                        md.setAutorityFile("Source", currentSource.getSourceId(), currentSource.getSourceUri());
                    }

                }
            }
        }
    }

    @Getter
    @Setter
    private String entitySearch;

    @Getter
    private List entities = new ArrayList<>();

    @Getter
    private EntityType entityType;

    public void addRelationship(EntityType type) {
        entitySearch = "";
        entityType = type;
        entities.clear();
        // TODO open modal, search field for entities of the type

        // TODO get all relationship types for the link type
    }

    public void searchEntity() {
        //  search for all metadata with type: entityType and title/metadata: entitySearch
        String sql = "select processid, name, value from metadata where processid in (select processid from metadata where name=\"index."
                + entityType.getName() + "Search\" and value like \"%" + StringEscapeUtils.escapeSql(entitySearch) + "%\" )";

        Map<String, Map<String, String>> metadataMap = new HashMap<>();

        List<?> rows = ProcessManager.runSQL(sql);
        for (Object obj : rows) {
            Object[] objArr = (Object[]) obj;
            String processid = (String) objArr[0];
            String metadataName = (String) objArr[1];
            String metadataValue = (String) objArr[2];
            System.out.println(processid + ", " + metadataName + ": " + metadataValue);
        }

        // or search for processes, load metadata from mets file?
        sql = "select processid from metadata where name=\"docstruct\" and value =\"" + entityType.getName()
        + "\" and  processid in (select processid from metadata where name=\"index." + entityType.getName() + "Search\" and value like \"%"
        + StringEscapeUtils.escapeSql(entitySearch) + "%\" )";
        // load mets files, open entities

    }

    public void createEntity(EntityType type) {
        // TODO save + close current entity
        // TODO create breadcrumb
        // TODO create new entity with given type

    }

    public void saveEntity() {
        // TODO save metadata
    }

}
