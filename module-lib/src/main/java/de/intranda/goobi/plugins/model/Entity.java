package de.intranda.goobi.plugins.model;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.goobi.beans.GoobiProperty;
import org.goobi.beans.GoobiProperty.PropertyOwnerType;
import org.goobi.beans.Process;
import org.goobi.production.cli.helper.StringPair;

import de.intranda.goobi.plugins.model.MetadataField.SourceField;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.PropertyManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataGroupType;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsMods;

@Getter
@Log4j2
public class Entity {

    // contains the display name for the current entity
    private String entityName;

    // entity type of the current object
    private EntityType currentType;

    // store the current object
    private Process currentProcess;

    private Fileformat currentFileformat;
    private Prefs prefs;
    private EntityConfig configuration;

    @Setter
    private boolean selected = false;

    private Map<EntityType, List<Relationship>> linkedRelationships;

    // list of all metadata fields
    private List<ConfiguredField> metadataFieldList = new ArrayList<>();

    private GoobiProperty statusProperty = null;
    private GoobiProperty displayNameProperty = null;

    public Entity(EntityConfig configuration, Process process) {
        this.configuration = new EntityConfig(configuration);
        this.currentProcess = process;
        if (process.getEigenschaftenSize() > 0) {
            for (GoobiProperty property : process.getProperties()) {
                if ("ProcessStatus".equals(property.getPropertyName())) {
                    statusProperty = property;
                } else if ("DisplayName".equals(property.getPropertyName())) {
                    displayNameProperty = property;
                }
            }
        }

        if (statusProperty == null) {
            statusProperty = new GoobiProperty(PropertyOwnerType.PROCESS);
            statusProperty.setPropertyName("ProcessStatus");
            statusProperty.setOwner(process);
            statusProperty.setPropertyValue("New");
            process.getEigenschaften().add(statusProperty);
            PropertyManager.saveProperty(statusProperty);
        }

        if (displayNameProperty == null) {
            displayNameProperty = new GoobiProperty(PropertyOwnerType.PROCESS);
            displayNameProperty.setPropertyName("DisplayName");
            displayNameProperty.setOwner(process);
            process.getEigenschaften().add(displayNameProperty);
            PropertyManager.saveProperty(displayNameProperty);
        }

        try {
            prefs = currentProcess.getRegelsatz().getPreferences();
            currentFileformat = new MetsMods(prefs);
            currentFileformat.read(currentProcess.getMetadataFilePath());
        } catch (UGHException | IOException | SwapException e) {
            log.error(e);
            throw new IllegalArgumentException();
        }
        readMetadata();
    }

    // read metadata from fileformat and initilize fields for UI
    private void readMetadata() {
        try {
            DocStruct logical = currentFileformat.getDigitalDocument().getLogicalDocStruct();
            String entityType = logical.getType().getName();
            currentType = configuration.getTypeByName(entityType);
            generateDisplayName(logical, entityType);

            metadataFieldList.clear();

            for (ConfiguredField mf : new ArrayList<>(currentType.getConfiguredFields())) {
                mf.clearMetadata(this);
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
                            if (!"Source".equals(subfield.getMetadataName())) {
                                List<Metadata> mdl = group.getMetadataByType(subfield.getMetadataName());
                                Metadata metadata = null;
                                if (mdl != null && !mdl.isEmpty()) {
                                    metadata = mdl.get(0);
                                } else {
                                    MetadataType metadataType = prefs.getMetadataTypeByName(subfield.getMetadataName());
                                    metadata = new Metadata(metadataType);
                                    if (StringUtils.isNotBlank(subfield.getDefaultValue())) {
                                        metadata.setValue(subfield.getDefaultValue());
                                    }
                                    group.addMetadata(metadata);
                                }
                                MetadataField sub = new MetadataField();
                                sub.setConfigField(subfield);
                                sub.setMetadata(metadata);
                                subfield.adMetadataField(sub);
                                field.addSubField(sub);
                            } else {
                                field.setAllowSources(true);
                            }
                        }

                    } else {
                        metadataFieldList.add(mf);
                        for (MetadataGroup group : groups) {
                            MetadataField field = new MetadataField();
                            field.setConfigField(mf);
                            field.setGroup(group);
                            mf.adMetadataField(field);
                            mf.setShowField(true);
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
                                                if ("SourceID".equals(md.getType().getName())) {
                                                    sourceId = md.getValue();
                                                    sourceUri = md.getAuthorityURI();
                                                } else if ("SourceName".equals(md.getType().getName())) {
                                                    sourceName = md.getValue();
                                                } else if ("SourceType".equals(md.getType().getName())) {
                                                    sourceType = md.getValue();
                                                } else if ("SourceLink".equals(md.getType().getName())) {
                                                    sourceLink = md.getValue();
                                                } else if ("SourcePage".equals(md.getType().getName())) {
                                                    sourcePageRange = md.getValue();
                                                }

                                            }
                                            SourceField source =
                                                    field.new SourceField(sourceId, sourceUri, sourceName, sourceType, sourceLink, sourcePageRange);
                                            field.addSource(source, null);
                                        }
                                    }

                                } else {
                                    MetadataType metadataType = prefs.getMetadataTypeByName(subfield.getMetadataName());
                                    List<Metadata> mdl = group.getMetadataByType(subfield.getMetadataName());
                                    if (mdl.isEmpty()) {
                                        // create new metadata
                                        Metadata metadata = new Metadata(metadataType);
                                        if (StringUtils.isNotBlank(subfield.getDefaultValue())) {
                                            metadata.setValue(subfield.getDefaultValue());
                                        }
                                        group.addMetadata(metadata);
                                        MetadataField sub = new MetadataField();
                                        sub.setConfigField(subfield);
                                        sub.setMetadata(metadata);
                                        subfield.adMetadataField(sub);
                                        field.addSubField(sub);
                                        // generate metadata value
                                        if ("generated".equals(subfield.getFieldType())) {
                                            GenerationRule rule = subfield.getGenerationRule();
                                            if (rule != null) {
                                                VariableReplacer replacer =
                                                        new VariableReplacer(currentFileformat.getDigitalDocument(), prefs, currentProcess, null);
                                                String replacedValue = rule.generate(replacer);
                                                if (StringUtils.isNotBlank(replacedValue)) {
                                                    metadata.setValue(replacedValue);
                                                    mf.setShowField(true);
                                                }
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
                        if (StringUtils.isNotBlank(mf.getDefaultValue())) {
                            metadata.setValue(mf.getDefaultValue());
                        }

                        logical.addMetadata(metadata);
                        MetadataField field = new MetadataField();
                        field.setConfigField(mf);
                        field.setMetadata(metadata);
                        mf.adMetadataField(field);
                        metadataFieldList.add(mf);
                        // generate metadata value
                        if ("generated".equals(mf.getFieldType())) {
                            GenerationRule rule = mf.getGenerationRule();
                            if (rule != null) {
                                VariableReplacer replacer = new VariableReplacer(currentFileformat.getDigitalDocument(), prefs, currentProcess, null);
                                String value = rule.generate(replacer);
                                if (StringUtils.isNotBlank(value)) {
                                    metadata.setValue(replacer.replace(value));
                                    mf.setShowField(true);
                                }
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
                        }
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

                StringBuilder processids = new StringBuilder();

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
                    String valueUrl = null;
                    String sourceType = null;
                    String awardTier = null;
                    String awardTierUri = null;
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
                            if (StringUtils.isNumeric(processId)) {
                                if (processids.length() > 0) {
                                    processids.append(", ");
                                }
                                processids.append(processId);
                            }
                        } else if (metadataType.equals(configuration.getRelationshipDisplayName())) {
                            displayName = md.getValue();
                        } else if (metadataType.equals(configuration.getRelationshipType())) {
                            type = md.getValue();
                            vocabularyName = md.getAuthorityID();
                            vocabularyUrl = md.getAuthorityURI();
                            valueUrl = md.getAuthorityValue();
                        } else if (StringUtils.isNotBlank(configuration.getRelationshipSourceType())
                                && metadataType.equals(configuration.getRelationshipSourceType())) {
                            sourceType = md.getValue();
                        } else if (StringUtils.isNotBlank(configuration.getRelationshipTierType())
                                && metadataType.equals(configuration.getRelationshipTierType())) {
                            awardTier = md.getValue();
                            awardTierUri = md.getAuthorityValue();
                        }

                    }
                    Relationship relationship = new Relationship();
                    relationship.setEntityName(entity);
                    relationship.setBeginningDate(beginningDate);
                    relationship.setEndDate(endDate);
                    relationship.setAdditionalData(additionalData);
                    relationship.setSourceType(sourceType);
                    relationship.setProcessId(processId);
                    relationship.setAwardTier(awardTier);
                    relationship.setAwardTierUri(awardTierUri);

                    for (RelationshipType rel : currentType.getConfiguredRelations()) {
                        if (rel.getVocabularyName().equals(vocabularyName) && rel.getRelationshipNameEn().equals(type)) {
                            relationship.setType(rel);
                            break;
                        }
                    }
                    // If the relationship type wasn't found, it might have been saved in the wrong direction in the past.
                    // Try to find the reverse direction and inform the user about the automatic correction on a hit.
                    if (relationship.getType() == null) {
                        for (RelationshipType rel : currentType.getConfiguredRelations()) {
                            if (rel.getVocabularyName().equals(vocabularyName) && StringUtils.isNotBlank(rel.getReversedRelationshipNameEn())
                                    && rel.getReversedRelationshipNameEn().equals(type)) {
                                relationship.setType(rel);
                                String warnMessage = "Relation type \"" + type
                                        + "\" was read from the metadata, but the current entity type only supports the relation type \""
                                        + rel.getRelationshipNameEn()
                                        + "\". This has been automatically corrected. If this is right, please save the entity. If not, please resolve the issue manually.";
                                log.warn(warnMessage);
                                Helper.setMeldung(warnMessage);
                                break;
                            }
                        }
                    }
                    if (relationship.getType() == null) {
                        String errorMessage =
                                "Relation type \"" + type + "\" is not present in the configured vocabulary \"" + vocabularyName + "\".";
                        log.error(errorMessage);
                        Helper.setFehlerMeldung(errorMessage);
                    }

                    relationship.setDisplayName(displayName);
                    relationship.setVocabularyName(vocabularyName);
                    relationship.setVocabularyUrl(vocabularyUrl);
                    relationship.setValueUrl(valueUrl);
                    relationship.setMetadataGroup(group);
                    for (EntityType et : linkedRelationships.keySet()) {
                        if (et.getName().equals(relationship.getEntityName())) {
                            linkedRelationships.get(et).add(relationship);
                        }
                    }
                }
                if (processids.length() > 0) {
                    // get current status from database
                    String sql = "select object_id, property_value from properties where property_name = 'ProcessStatus' and object_id in ("
                            + processids.toString() + ")";
                    List<?> rows = ProcessManager.runSQL(sql);
                    for (Object obj : rows) {
                        Object[] objArr = (Object[]) obj;
                        String processid = (String) objArr[0];
                        String processStatus = (String) objArr[1];

                        for (EntityType et : linkedRelationships.keySet()) {
                            for (Relationship r : linkedRelationships.get(et)) {
                                if (r.getProcessId().equals(processid)) {
                                    r.setProcessStatus(processStatus);
                                }
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
    public void generateDisplayName(DocStruct logical, String entityType) {
        // read main name from metadata

        String configuredLanguages = currentType.getLanguageOrder();
        List<String> languages = new ArrayList<>();
        boolean checkLanguages = false;
        if (StringUtils.isNotBlank(configuredLanguages)) {
            checkLanguages = true;
            for (String lang : configuredLanguages.split(",")) {
                languages.add(lang.trim());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String metadata : currentType.getIdentifyingMetadata().split(" ")) {
            if (metadata.contains("/")) {
                String[] parts = metadata.split("/");
                // first part -> group name
                if (logical.getAllMetadataGroups() != null) {
                    for (MetadataGroup mg : logical.getAllMetadataGroups()) {
                        if (mg.getType().getName().equals(parts[0])) {
                            // last part metadata name
                            if (checkLanguages) {
                                boolean found = false;
                                for (String lang : languages) {
                                    if (!found) {
                                        for (Metadata md : mg.getMetadataList()) {
                                            if (StringUtils.isNotBlank(md.getValue())) {
                                                if ((parts[1] + lang).equalsIgnoreCase(md.getType().getName())) {
                                                    if (sb.length() > 0) {
                                                        sb.append(" ");
                                                    }
                                                    sb.append(md.getValue());
                                                    found = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                for (Metadata md : mg.getMetadataList()) {
                                    if (StringUtils.isNotBlank(md.getValue()) && md.getType().getName().equals(parts[1])) {
                                        if (sb.length() > 0) {
                                            sb.append(" ");
                                        }
                                        sb.append(md.getValue());
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (checkLanguages) {

            } else {
                for (Metadata md : logical.getAllMetadata()) {
                    if (StringUtils.isNotBlank(md.getValue()) && md.getType().getName().equals(metadata)) {
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
                    if ("source".equals(subfield.getFieldType())) {
                        f.setAllowSources(true);
                    }
                    if (!subfield.isGroup()) {
                        Metadata otherMetadata = subfield.getMetadataList().get(0).getMetadata();
                        MetadataType metadataType = otherMetadata.getType();
                        List<Metadata> defaultMetadata = group.getMetadataByType(metadataType.getName());
                        Metadata metadata = null;
                        if (!defaultMetadata.isEmpty()) {
                            metadata = defaultMetadata.get(0);
                        } else {
                            metadata = new Metadata(metadataType);
                            group.addMetadata(metadata);
                        }
                        if (StringUtils.isNotBlank(subfield.getDefaultValue())) {
                            metadata.setValue(subfield.getDefaultValue());
                        }
                        MetadataField sub = new MetadataField();
                        sub.setConfigField(subfield);
                        sub.setMetadata(metadata);
                        f.addSubField(sub);
                    }
                }
            }

            catch (UGHException e) {
                log.error(e);
            }
        } else {
            try {
                Metadata metadata = new Metadata(prefs.getMetadataTypeByName(field.getMetadataName()));
                if (StringUtils.isNotBlank(field.getDefaultValue())) {
                    metadata.setValue(field.getDefaultValue());
                }
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
            for (ConfiguredField sub : cf.getSubfieldList()) {
                if ("fileupload".equals(sub.getFieldType())) {
                    try {
                        String imageName = sub.getMetadataList().get(0).getMetadata().getValue();
                        StorageProvider.getInstance().deleteFile(Paths.get(imageName));

                        // TODO if image conversion and derivate is used, remove original file as well
                    } catch (Exception e) {
                        log.error(e);
                    }

                }
            }

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
            if ("Bibliography".equals(cf.getMetadataName())) {
                bibliography = cf;
            }
        }

        if (bibliography == null) {
            //no bibliography entered. Nothing to generate
            return;
        }

        // create bibliography from exported sources
        for (SourceField currentSource : allSources) {
            boolean sourceMatched = false;
            String sourceId = currentSource.getSourceId();

            for (MetadataField mf : bibliography.getMetadataList()) {
                for (Metadata md : mf.getGroup().getMetadataList()) {
                    if ("SourceID".equals(md.getType().getName()) && sourceId.equals(md.getValue())) {
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
                    if ("Citation".equals(subfield.getConfigField().getLabel())) {
                        subfield.getMetadata().setValue(currentSource.getSourceName());
                        // TODO distinct between source type and bibliography type
                    } else if ("Type".equals(subfield.getConfigField().getLabel())) {
                        subfield.getMetadata().setValue(currentSource.getSourceType());
                    } else if ("Link".equals(subfield.getConfigField().getLabel())) {
                        subfield.getMetadata().setValue(currentSource.getSourceLink());
                    } else if ("SourceID".equals(subfield.getConfigField().getLabel())) {
                        Metadata md = subfield.getMetadata();
                        md.setValue(currentSource.getSourceId());
                        md.setAuthorityFile("Source", currentSource.getSourceId(), currentSource.getSourceUri());
                    }

                }
            }
        }
    }

    public boolean isShowGenerateBibliographyButton() {
        for (ConfiguredField cf : metadataFieldList) {
            for (MetadataField mf : cf.getMetadataList()) {
                List<SourceField> sourceFields = mf.getSources();
                if (!sourceFields.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void saveEntity() {
        try {

            // relationships
            for (List<Relationship> rellist : linkedRelationships.values()) {
                for (Relationship rel : rellist) {
                    upateRelationshipGroup(rel);

                }
            }

            if ("New".equals(statusProperty.getPropertyValue())) {
                statusProperty.setPropertyValue("In work");
            }
            statusProperty.setCreationDate(new Date());

            // check if the display name was changed, in this case linked entities must be updated
            boolean updatedName = !entityName.equals(displayNameProperty.getPropertyValue()) && !entityName.equals(currentType.getName());

            displayNameProperty.setPropertyValue(entityName);
            writeSourcePropertiesToMetadata();

            currentProcess.writeMetadataFile(currentFileformat);
            if (updatedName && configuration.isUpdateProcessTitle()) {
                currentProcess.changeProcessTitle(entityName.toLowerCase().replaceAll("\\W", "_"));
            }

            ProcessManager.saveProcess(currentProcess);

            // update link name in other entities
            if (updatedName) {

                for (EntityType type : linkedRelationships.keySet()) {
                    List<Relationship> relationships = linkedRelationships.get(type);

                    for (Relationship r : relationships) {
                        MetsMods other = new MetsMods(prefs);
                        String metsFile = ConfigurationHelper.getInstance().getMetadataFolder() + r.getProcessId() + "/meta.xml";
                        other.read(metsFile);

                        DocStruct logical = other.getDigitalDocument().getLogicalDocStruct();
                        if (logical.getAllMetadataGroups() != null) {
                            for (MetadataGroup relationGroup : logical.getAllMetadataGroups()) {
                                if (relationGroup.getType().getName().equals(configuration.getRelationshipMetadataName())) {

                                    List<Metadata> mdl = relationGroup.getMetadataByType(configuration.getRelationshipProcessId());
                                    if (mdl != null && !mdl.isEmpty()) {
                                        String processId = mdl.get(0).getValue();
                                        if (processId.equals(String.valueOf(currentProcess.getId()))) {
                                            mdl = relationGroup.getMetadataByType(configuration.getRelationshipDisplayName());
                                            if (mdl != null && !mdl.isEmpty()) {
                                                mdl.get(0).setValue(entityName);
                                                break;
                                            }

                                        }
                                    }
                                }
                            }
                        }
                        other.write(metsFile);
                    }
                }
            }

        } catch (UGHException | IOException | SwapException | DAOException e) {
            log.error(e);
        }
    }

    public void writeSourcePropertiesToMetadata() throws MetadataTypeNotAllowedException {
        for (ConfiguredField configuredField : metadataFieldList) {
            for (MetadataField metadataField : configuredField.getMetadataList()) {
                for (SourceField sourceField : metadataField.getSources()) {
                    int sourceIndex = metadataField.getSources().indexOf(sourceField);
                    if (sourceIndex >= 0) {
                        List<MetadataGroup> sourceGroups =
                                metadataField.getGroup().getAllMetadataGroupsByType(prefs.getMetadataGroupTypeByName("Source"));
                        if (sourceGroups != null && sourceIndex < sourceGroups.size()) {
                            MetadataGroup sourceGroup = sourceGroups.get(sourceIndex);

                            Metadata sourceTypeMetadata = Optional.ofNullable(sourceGroup.getMetadataByType("SourceType"))
                                    .flatMap(list -> list.stream().findFirst())
                                    .orElse(null);
                            if (sourceTypeMetadata == null) {
                                sourceTypeMetadata = new Metadata(prefs.getMetadataTypeByName("SourceType"));
                                sourceGroup.addMetadata(sourceTypeMetadata);
                            }
                            sourceTypeMetadata.setValue(sourceField.getSourceType());

                            Metadata sourcePageMetadata = Optional.ofNullable(sourceGroup.getMetadataByType("SourcePage"))
                                    .flatMap(list -> list.stream().findFirst())
                                    .orElse(null);
                            if (sourcePageMetadata == null) {
                                sourcePageMetadata = new Metadata(prefs.getMetadataTypeByName("SourcePage"));
                                sourceGroup.addMetadata(sourcePageMetadata);
                            }
                            sourcePageMetadata.setValue(sourceField.getPageRange());
                        }
                    }
                }
            }
        }
    }

    public void addRelationship(Entity selectedEntity, String relationshipData, String relationshipStartDate, String relationshipEndDate,
            RelationshipType selectedRelationship, String relationshipSourceType, String tierLabel, String tierUri) {
        List<Relationship> relationships = linkedRelationships.get(selectedEntity.getCurrentType());
        String relationshipStatus = selectedEntity.getStatusProperty().getPropertyName();

        Relationship rel = new Relationship();
        rel.setAdditionalData(relationshipData);
        rel.setBeginningDate(relationshipStartDate);
        rel.setEndDate(relationshipEndDate);
        rel.setDisplayName(selectedEntity.getEntityName());
        rel.setEntityName(selectedEntity.getCurrentType().getName());
        rel.setProcessId(String.valueOf(selectedEntity.getCurrentProcess().getId()));
        rel.setProcessStatus(relationshipStatus);
        rel.setType(selectedRelationship);
        rel.setSourceType(relationshipSourceType);
        rel.setVocabularyName(selectedRelationship.getVocabularyName());
        rel.setVocabularyUrl(selectedRelationship.getVocabularyUrl());
        rel.setValueUrl(selectedRelationship.getValueUrl());
        rel.setAwardTier(tierLabel);
        rel.setAwardTierUri(tierUri);

        MetadataGroup relationGroup = null;

        try {
            relationGroup = new MetadataGroup(prefs.getMetadataGroupTypeByName(configuration.getRelationshipMetadataName()));
            rel.setMetadataGroup(relationGroup);

            upateRelationshipGroup(rel);

            currentFileformat.getDigitalDocument().getLogicalDocStruct().addMetadataGroup(relationGroup);

        } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException | PreferencesException e) {
            log.error(e);
        }

        relationships.add(rel);

    }

    private void upateRelationshipGroup(Relationship rel) throws MetadataTypeNotAllowedException {
        MetadataGroup relationGroup = rel.getMetadataGroup();
        List<Metadata> mdl = relationGroup.getMetadataByType(configuration.getRelationshipEntityType());
        if (mdl != null && !mdl.isEmpty()) {
            mdl.get(0).setValue(rel.getEntityName());
        } else {
            Metadata md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipEntityType()));
            relationGroup.addMetadata(md);
            md.setValue(rel.getEntityName());
        }
        mdl = relationGroup.getMetadataByType(configuration.getRelationshipBeginningDate());
        if (mdl != null && !mdl.isEmpty()) {
            mdl.get(0).setValue(rel.getBeginningDate());
        } else {
            Metadata md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipBeginningDate()));
            relationGroup.addMetadata(md);
            md.setValue(rel.getBeginningDate());
        }

        mdl = relationGroup.getMetadataByType(configuration.getRelationshipEndDate());
        if (mdl != null && !mdl.isEmpty()) {
            mdl.get(0).setValue(rel.getEndDate());
        } else {
            Metadata md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipEndDate()));
            relationGroup.addMetadata(md);
            md.setValue(rel.getEndDate());
        }

        mdl = relationGroup.getMetadataByType(configuration.getRelationshipProcessId());
        if (mdl != null && !mdl.isEmpty()) {
            mdl.get(0).setValue(rel.getProcessId());
        } else {
            Metadata md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipProcessId()));
            relationGroup.addMetadata(md);
            md.setValue(rel.getProcessId());
        }

        mdl = relationGroup.getMetadataByType(configuration.getRelationshipDisplayName());
        if (mdl != null && !mdl.isEmpty()) {
            mdl.get(0).setValue(rel.getDisplayName());
        } else {
            Metadata md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipDisplayName()));
            relationGroup.addMetadata(md);
            md.setValue(rel.getDisplayName());
        }
        Metadata md = null;
        mdl = relationGroup.getMetadataByType(configuration.getRelationshipType());
        if (mdl != null && !mdl.isEmpty()) {
            md = mdl.get(0);
        } else {
            md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipType()));
            relationGroup.addMetadata(md);
        }

        if (StringUtils.isNotBlank(rel.getAwardTier()) && StringUtils.isNotBlank(configuration.getRelationshipTierType())) {
            Metadata tier = null;
            mdl = relationGroup.getMetadataByType(configuration.getRelationshipTierType());
            if (mdl != null && !mdl.isEmpty()) {
                tier = mdl.get(0);
            } else {
                tier = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipTierType()));
                relationGroup.addMetadata(tier);
            }
            tier.setValue(rel.getAwardTier());
            tier.setAuthorityFile(configuration.getRelationshipTierVocabulary(), configuration.getRelationshipTierVocabularyUri(),
                    rel.getAwardTierUri());
        }

        if (rel.getType() == null) {
            throw new IllegalStateException(
                    "The relationtype has not been set properly. This might have been caused due to a relation configured in the mets file, that is not present in the vocabulary (anymore)");
        }
        md.setValue(rel.getType().getRelationshipNameEn());
        md.setAuthorityFile(rel.getVocabularyName(), rel.getVocabularyUrl(), rel.getValueUrl());

        mdl = relationGroup.getMetadataByType(configuration.getRelationshipAdditionalData());
        if (mdl != null && !mdl.isEmpty()) {
            mdl.get(0).setValue(rel.getAdditionalData());
        } else {
            md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipAdditionalData()));
            relationGroup.addMetadata(md);
            md.setValue(rel.getAdditionalData());
        }

        if (StringUtils.isNotBlank(configuration.getRelationshipSourceType())) {
            mdl = relationGroup.getMetadataByType(configuration.getRelationshipSourceType());
            if (mdl != null && !mdl.isEmpty()) {
                mdl.get(0).setValue(rel.getSourceType());
            } else {
                md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipSourceType()));
                relationGroup.addMetadata(md);
                md.setValue(rel.getSourceType());
            }
        }
    }

    public void createLinkedEntityFile(Workbook wb, boolean followRelationships) {
        Locale locale = Helper.getSessionLocale();
        int rowNum = 0;

        Sheet sheet = wb.createSheet(entityName.replaceAll("[:\\*?/\\]\\[]", ""));

        rowNum = createAgentSection(locale, rowNum, sheet);

        // blank rows
        sheet.createRow(rowNum++);
        sheet.createRow(rowNum++);

        // linked persons
        rowNum = createPersonSection(locale, rowNum, sheet);

        // blank rows
        sheet.createRow(rowNum++);
        sheet.createRow(rowNum++);

        // linked events

        rowNum = createEventSection(locale, rowNum, sheet);

        // blank rows
        sheet.createRow(rowNum++);
        sheet.createRow(rowNum++);
        // linked awards

        createAwardSection(locale, rowNum, sheet);

        if (followRelationships) {
            // add an additional sheet for every linked relationship
            for (List<Relationship> relationships : linkedRelationships.values()) {
                for (Relationship rel : relationships) {
                    // initialize entity
                    Process process = ProcessManager.getProcessById(Integer.parseInt(rel.getProcessId()));
                    Entity other = new Entity(configuration, process);
                    // add sheet
                    other.createLinkedEntityFile(wb, false);
                }
            }
        }
    }

    public int createAgentSection(Locale locale, int rowNum, Sheet sheet) {
        Row firstRow = sheet.createRow(rowNum++);
        Cell c = firstRow.createCell(0);
        c.setCellValue("Linked Agents");

        Row agentTitleRow = sheet.createRow(rowNum++);
        Cell agentTitleCell1 = agentTitleRow.createCell(1);
        agentTitleCell1.setCellValue("Relationship Type");
        Cell agentTitleCell2 = agentTitleRow.createCell(2);
        agentTitleCell2.setCellValue("Primary Role");
        Cell agentTitleCell3 = agentTitleRow.createCell(3);
        agentTitleCell3.setCellValue("Main Name Original");
        Cell agentTitleCell4 = agentTitleRow.createCell(4);
        agentTitleCell4.setCellValue("Location (Geonames)");
        Cell agentTitleCell5 = agentTitleRow.createCell(5);
        agentTitleCell5.setCellValue("Artistic Movement");
        Cell agentTitleCell6 = agentTitleRow.createCell(6);
        agentTitleCell6.setCellValue("Discipline");

        // iterate over all linked agents
        for (EntityType et : linkedRelationships.keySet()) {
            if ("Agent".equalsIgnoreCase(et.getName())) {
                List<Relationship> relationships = linkedRelationships.get(et);
                for (Relationship rel : relationships) {
                    rowNum = createAgentRow(locale, rowNum, sheet, rel);
                }
            }
        }
        return rowNum;
    }

    public int createAgentRow(Locale locale, int rowNum, Sheet sheet, Relationship rel) {
        Row dataRow = sheet.createRow(rowNum++);
        Cell type = dataRow.createCell(1);
        type.setCellValue(rel.getLabel(locale));
        List<StringPair> metadata = MetadataManager.getMetadata(Integer.parseInt(rel.getProcessId()));
        StringBuilder role = new StringBuilder("");
        StringBuilder mainName = new StringBuilder("");
        StringBuilder location = new StringBuilder("");
        StringBuilder movement = new StringBuilder("");
        StringBuilder discipline = new StringBuilder("");
        for (StringPair sp : metadata) {
            switch (sp.getOne()) {
                case "PrimaryRole":
                    role.append(sp.getTwo());
                    break;
                case "NameORIG":
                    mainName.append(sp.getTwo());
                    break;
                case "Location":
                    if (!location.isEmpty()) {
                        location.append("; ");
                    }
                    location.append(sp.getTwo());
                    break;
                case "ArtisticMovement":
                    if (!movement.isEmpty()) {
                        movement.append("; ");
                    }
                    movement.append(sp.getTwo());
                    break;
                case "Discipline":
                    if (!discipline.isEmpty()) {
                        discipline.append("; ");
                    }
                    discipline.append(sp.getTwo());
                    break;
                default:
                    break;
            }
        }
        Cell cell2 = dataRow.createCell(2);
        cell2.setCellValue(role.toString());
        Cell cell3 = dataRow.createCell(3);
        cell3.setCellValue(mainName.toString());
        Cell cell4 = dataRow.createCell(4);
        cell4.setCellValue(location.toString());
        Cell cell5 = dataRow.createCell(5);
        cell5.setCellValue(movement.toString());
        Cell cell6 = dataRow.createCell(6);
        cell6.setCellValue(discipline.toString());
        return rowNum;
    }

    public int createPersonSection(Locale locale, int rowNum, Sheet sheet) {
        Row personRow = sheet.createRow(rowNum++);
        Cell pc = personRow.createCell(0);
        pc.setCellValue("Linked Persons");
        Row personTitleRow = sheet.createRow(rowNum++);
        Cell personTitleRow1 = personTitleRow.createCell(1);
        personTitleRow1.setCellValue("Relationship Type");
        Cell personTitleRow2 = personTitleRow.createCell(2);
        personTitleRow2.setCellValue("Primary Role");
        Cell personTitleRow3 = personTitleRow.createCell(3);
        personTitleRow3.setCellValue("Main Name Original");
        Cell personTitleRow4 = personTitleRow.createCell(4);
        personTitleRow4.setCellValue("Date of Birth");
        Cell personTitleRow5 = personTitleRow.createCell(5);
        personTitleRow5.setCellValue("Place of Birth");
        Cell personTitleRow6 = personTitleRow.createCell(6);
        personTitleRow6.setCellValue("Date of Death");
        Cell personTitleRow7 = personTitleRow.createCell(7);
        personTitleRow7.setCellValue("Place of Death");
        Cell personTitleRow8 = personTitleRow.createCell(8);
        personTitleRow8.setCellValue("Gender");
        Cell personTitleRow9 = personTitleRow.createCell(9);
        personTitleRow9.setCellValue("Discipline");
        Cell personTitleRow10 = personTitleRow.createCell(10);
        personTitleRow10.setCellValue("Artistic Movement");

        for (EntityType et : linkedRelationships.keySet()) {
            if ("Person".equalsIgnoreCase(et.getName())) {
                List<Relationship> relationships = linkedRelationships.get(et);
                for (Relationship rel : relationships) {
                    rowNum = createPersonRow(locale, rowNum, sheet, rel);
                }
            }
        }
        return rowNum;
    }

    public int createPersonRow(Locale locale, int rowNum, Sheet sheet, Relationship rel) {
        Row dataRow = sheet.createRow(rowNum++);
        Cell type = dataRow.createCell(1);
        type.setCellValue(rel.getLabel(locale));
        List<StringPair> metadata = MetadataManager.getMetadata(Integer.parseInt(rel.getProcessId()));
        String role = "";
        String firstname = "";
        String lastname = "";
        String birthDate = "";
        String birthPlace = "";
        String deathDate = "";
        String deathPlace = "";
        String gender = "";
        StringBuilder discipline = new StringBuilder("");
        StringBuilder movement = new StringBuilder("");
        for (StringPair sp : metadata) {
            switch (sp.getOne()) {
                case "PrimaryRole":
                    role = sp.getTwo();
                    break;
                case "FirstnameOrig":
                    firstname = sp.getTwo();
                    break;
                case "LastnameOrig":
                    lastname = sp.getTwo();
                    break;
                case "Birthdate":
                    birthDate = sp.getTwo();
                    break;
                case "Birthplace":
                    birthPlace = sp.getTwo();
                    break;
                case "DeathDate":
                    deathDate = sp.getTwo();
                    break;
                case "Deathplace":
                    deathPlace = sp.getTwo();
                    break;
                case "Gender":
                    gender = sp.getTwo();
                    break;
                case "ArtisticMovement":
                    if (!movement.isEmpty()) {
                        movement.append("; ");
                    }
                    movement.append(sp.getTwo());
                    break;
                case "Discipline":
                    if (!discipline.isEmpty()) {
                        discipline.append("; ");
                    }
                    discipline.append(sp.getTwo());
                    break;
                default:
                    break;
            }
        }
        Cell cell2 = dataRow.createCell(2);
        cell2.setCellValue(role);
        Cell cell3 = dataRow.createCell(3);
        cell3.setCellValue(lastname + ", " + firstname);
        Cell cell4 = dataRow.createCell(4);
        cell4.setCellValue(birthDate);
        Cell cell5 = dataRow.createCell(5);
        cell5.setCellValue(birthPlace);
        Cell cell6 = dataRow.createCell(6);
        cell6.setCellValue(deathDate);
        Cell cell7 = dataRow.createCell(7);
        cell7.setCellValue(deathPlace);
        Cell cell8 = dataRow.createCell(8);
        cell8.setCellValue(gender);
        Cell cell9 = dataRow.createCell(9);
        cell9.setCellValue(discipline.toString());
        Cell cell10 = dataRow.createCell(10);
        cell10.setCellValue(movement.toString());
        return rowNum;
    }

    public int createEventSection(Locale locale, int rowNum, Sheet sheet) {
        Row eventRow = sheet.createRow(rowNum++);
        Cell ec = eventRow.createCell(0);
        ec.setCellValue("Linked Events");
        Row eventTitleRow = sheet.createRow(rowNum++);
        Cell eventTitle1 = eventTitleRow.createCell(1);
        eventTitle1.setCellValue("Relationship Type");
        Cell eventTitle2 = eventTitleRow.createCell(2);
        eventTitle2.setCellValue("Event Type");
        Cell eventTitle3 = eventTitleRow.createCell(3);
        eventTitle3.setCellValue("Title");
        Cell eventTitle4 = eventTitleRow.createCell(4);
        eventTitle4.setCellValue("Duration Beginning");
        Cell eventTitle5 = eventTitleRow.createCell(5);
        eventTitle5.setCellValue("Duration End");
        Cell eventTitle6 = eventTitleRow.createCell(6);
        eventTitle6.setCellValue("Location (Geonames)");
        Cell eventTitle7 = eventTitleRow.createCell(7);
        eventTitle7.setCellValue("Recurring Event");

        for (EntityType et : linkedRelationships.keySet()) {
            if ("Event".equalsIgnoreCase(et.getName())) {
                List<Relationship> relationships = linkedRelationships.get(et);
                for (Relationship rel : relationships) {
                    rowNum = createEventRow(locale, rowNum, sheet, rel);
                }
            }
        }
        return rowNum;
    }

    public int createEventRow(Locale locale, int rowNum, Sheet sheet, Relationship rel) {
        Row dataRow = sheet.createRow(rowNum++);
        Cell type = dataRow.createCell(1);
        type.setCellValue(rel.getLabel(locale));
        List<StringPair> metadata = MetadataManager.getMetadata(Integer.parseInt(rel.getProcessId()));
        String eventType = "";
        String title = "";
        String durationStart = rel.getBeginningDate() == null ? "" : rel.getBeginningDate();
        String durationEnd = rel.getEndDate() == null ? "" : rel.getEndDate();
        String location = "";
        String recurringEvent = "";
        for (StringPair sp : metadata) {
            switch (sp.getOne()) {
                case "EventType" -> eventType = sp.getTwo();
                case "NameORIG" -> title = sp.getTwo();
                case "Location" -> location = sp.getTwo();
                case "RecurringEvent" -> recurringEvent = sp.getTwo();
            }
        }

        Cell cell2 = dataRow.createCell(2);
        cell2.setCellValue(eventType);
        Cell cell3 = dataRow.createCell(3);
        cell3.setCellValue(title);
        Cell cell4 = dataRow.createCell(4);
        cell4.setCellValue(durationStart);
        Cell cell5 = dataRow.createCell(5);
        cell5.setCellValue(durationEnd);
        Cell cell6 = dataRow.createCell(6);
        cell6.setCellValue(location);
        Cell cell7 = dataRow.createCell(7);
        cell7.setCellValue(recurringEvent);
        return rowNum;
    }

    public void createAwardSection(Locale locale, int rowNum, Sheet sheet) {
        Row awardRow = sheet.createRow(rowNum++);
        Cell ac = awardRow.createCell(0);
        ac.setCellValue("Linked Awards");
        Row awardTitleRow = sheet.createRow(rowNum++);
        Cell awardtTitle1 = awardTitleRow.createCell(1);
        awardtTitle1.setCellValue("Relationship Type");
        Cell awardTitle2 = awardTitleRow.createCell(2);
        awardTitle2.setCellValue("Primary Role");
        Cell awardTitle3 = awardTitleRow.createCell(3);
        awardTitle3.setCellValue("Title");
        Cell awardTitle4 = awardTitleRow.createCell(4);
        awardTitle4.setCellValue("Award Type");
        Cell awardTitle5 = awardTitleRow.createCell(5);
        awardTitle5.setCellValue("Location");
        Cell awardTitle6 = awardTitleRow.createCell(6);
        awardTitle6.setCellValue(" Duration Beginning");
        Cell awardTitle7 = awardTitleRow.createCell(7);
        awardTitle7.setCellValue("Duration End");
        Cell awardTitle8 = awardTitleRow.createCell(8);
        awardTitle8.setCellValue("Degree");

        for (EntityType et : linkedRelationships.keySet()) {
            if ("Award".equalsIgnoreCase(et.getName())) {
                List<Relationship> relationships = linkedRelationships.get(et);
                for (Relationship rel : relationships) {
                    rowNum = createAwardRow(locale, rowNum, sheet, rel);
                }
            }
        }
    }

    public int createAwardRow(Locale locale, int rowNum, Sheet sheet, Relationship rel) {
        Row dataRow = sheet.createRow(rowNum++);
        Cell type = dataRow.createCell(1);
        type.setCellValue(rel.getLabel(locale));
        List<StringPair> metadata = MetadataManager.getMetadata(Integer.parseInt(rel.getProcessId()));
        String role = "";
        String title = "";
        String awardType = "";
        String location = "";
        String durationStart = rel.getBeginningDate() == null ? "" : rel.getBeginningDate();
        String durationEnd = rel.getEndDate() == null ? "" : rel.getEndDate();
        String degree = rel.getAwardTier() == null ? "" : rel.getAwardTier();

        for (StringPair sp : metadata) {
            switch (sp.getOne()) {
                case "EventType" -> role = sp.getTwo();
                case "TitleORIG" -> title = sp.getTwo();
                case "AwardType" -> awardType = sp.getTwo();
                case "Location" -> location = sp.getTwo();
            }
        }

        Cell cell2 = dataRow.createCell(2);
        cell2.setCellValue(role);
        Cell cell3 = dataRow.createCell(3);
        cell3.setCellValue(title);
        Cell cell4 = dataRow.createCell(4);
        cell4.setCellValue(awardType);
        Cell cell5 = dataRow.createCell(5);
        cell5.setCellValue(location);
        Cell cell6 = dataRow.createCell(6);
        cell6.setCellValue(durationStart);
        Cell cell7 = dataRow.createCell(7);
        cell7.setCellValue(durationEnd);
        Cell cell8 = dataRow.createCell(8);
        cell8.setCellValue(degree);
        return rowNum;
    }
}
