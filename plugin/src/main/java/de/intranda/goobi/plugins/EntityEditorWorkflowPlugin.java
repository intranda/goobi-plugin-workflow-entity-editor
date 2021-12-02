package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;
import org.goobi.vocabulary.Field;
import org.goobi.vocabulary.VocabRecord;

import de.intranda.goobi.plugins.model.BreadcrumbItem;
import de.intranda.goobi.plugins.model.ConfiguredField;
import de.intranda.goobi.plugins.model.EntityConfig;
import de.intranda.goobi.plugins.model.EntityConfig.EntityType;
import de.intranda.goobi.plugins.model.MetadataField;
import de.intranda.goobi.plugins.model.MetadataField.SourceField;
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
    private String value;

    @Getter
    private PluginType type = PluginType.Workflow;

    @Getter
    private String gui = "/uii/plugin_workflow_entity_editor.xhtml";

    @Getter
    private Process currentProcess;
    @Getter
    private Fileformat currentFileformat;
    @Getter
    private Prefs prefs;

    @Getter
    private EntityType currentType;

    @Getter
    private List<BreadcrumbItem> breadcrumbList = new ArrayList<>();

    @Getter
    @Setter
    private BreadcrumbItem selectedBreadcrumb;

    private EntityConfig configuration;

    @Getter
    private String entityName;

    @Getter
    private List<ConfiguredField> metadataFieldList = new ArrayList<>();

    /**
     * Constructor
     */
    public EntityEditorWorkflowPlugin() {
        XMLConfiguration config = ConfigPlugins.getPluginConfig(title);
        config.setExpressionEngine(new XPathExpressionEngine());

        configuration = new EntityConfig(config);

        loadTestdata();
    }

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

    public String loadSelectedBreadcrumb() {
        // TODO save current entity?

        //  check if dashboard was selected -> exit to start page
        if (0 == selectedBreadcrumb.getProcessId() && "Dashboard".equals(selectedBreadcrumb.getEntityName())) {
            return "/uii/index.xhtml";
        }

        // TODO: only if it doesn't exist in list?
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
                                            SourceField source = field.new SourceField(sourceId, sourceUri, sourceName, sourceType, sourceLink,
                                                    sourcePageRange);
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
            }

        } catch (UGHException e) {
            log.error(e);
        }

    }

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

    @Getter
    @Setter
    private MetadataField searchField;

    @Getter
    @Setter
    private String searchValue;

    @Getter
    private boolean showNotHits;

    @Getter
    private List<VocabRecord> records;
    @Getter
    @Setter
    private VocabRecord selectedVocabularyRecord;

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
}
