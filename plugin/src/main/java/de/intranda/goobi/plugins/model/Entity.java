package de.intranda.goobi.plugins.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;

import de.intranda.goobi.plugins.model.MetadataField.SourceField;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
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

    private Processproperty statusProperty = null;
    private Processproperty displayNameProperty = null;

    public Entity(EntityConfig configuration, Process process) {
        this.configuration = new EntityConfig(configuration);
        this.currentProcess = process;
        if (process.getEigenschaftenSize() > 0) {
            for (Processproperty property : process.getEigenschaften()) {
                if (property.getTitel().equals("ProcessStatus")) {
                    statusProperty = property;
                } else if (property.getTitel().equals("DisplayName")) {
                    displayNameProperty = property;
                }
            }
        }

        if (statusProperty == null) {
            statusProperty = new Processproperty();
            statusProperty = new Processproperty();
            statusProperty.setTitel("ProcessStatus");
            statusProperty.setProzess(process);
            statusProperty.setWert("New");
            process.getEigenschaften().add(statusProperty);
            PropertyManager.saveProcessProperty(statusProperty);
        }

        if (displayNameProperty == null) {
            displayNameProperty = new Processproperty();
            displayNameProperty = new Processproperty();
            displayNameProperty.setTitel("DisplayName");
            displayNameProperty.setProzess(process);
            process.getEigenschaften().add(displayNameProperty);
            PropertyManager.saveProcessProperty(displayNameProperty);
        }

        try {
            prefs = currentProcess.getRegelsatz().getPreferences();
            currentFileformat = new MetsMods(prefs);
            currentFileformat.read(currentProcess.getMetadataFilePath());
        } catch (UGHException | IOException | SwapException e) {
            log.error(e);
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
                            vocabularyUrl = md.getAuthorityValue();
                        }

                    }
                    Relationship relationship = new Relationship();
                    relationship.setEntityName(entity);
                    relationship.setBeginningDate(beginningDate);
                    relationship.setEndDate(endDate);
                    relationship.setAdditionalData(additionalData);
                    relationship.setProcessId(processId);

                    for (RelationshipType rel : currentType.getConfiguredRelations()) {
                        if (rel.getRelationshipNameEn().equals(type)) {
                            relationship.setType(rel);
                            break;
                        } else if (StringUtils.isNotBlank(rel.getReversedRelationshipNameEn()) && rel.getReversedRelationshipNameEn().equals(type)) {
                            relationship.setType(rel);
                            rel.setReversed(true);
                            relationship.setReverse(true);
                            break;
                        }
                    }

                    relationship.setDisplayName(displayName);
                    relationship.setVocabularyName(vocabularyName);
                    relationship.setVocabularyUrl(vocabularyUrl);
                    relationship.setMetadataGroup(group);
                    for (EntityType et : linkedRelationships.keySet()) {
                        if (et.getName().equals(relationship.getEntityName())) {
                            linkedRelationships.get(et).add(relationship);
                        }
                    }
                }
                if (processids.length() > 0) {
                    // get current status from database
                    String sql = "select prozesseID, Wert from prozesseeigenschaften where titel = 'ProcessStatus' and prozesseID in ("
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
                                                if (md.getType().getName().equalsIgnoreCase(parts[1] + lang)) {
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
                                    if (StringUtils.isNotBlank(md.getValue())) {
                                        if (md.getType().getName().equals(parts[1])) {
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
                }
            } else {
                if (checkLanguages) {

                } else {
                    for (Metadata md : logical.getAllMetadata()) {
                        if (StringUtils.isNotBlank(md.getValue())) {
                            if (md.getType().getName().equals(metadata)) {
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

            if (statusProperty.getWert().equals("New")) {
                statusProperty.setWert("In work");
            }
            statusProperty.setCreationDate(new Date());

            // check if the display name was changed, in this case linked entities must be updated
            boolean updatedName = !entityName.equals(displayNameProperty.getWert());

            displayNameProperty.setWert(entityName);


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
                        other.write(metsFile);
                    }
                }
            }

        } catch (UGHException | IOException | SwapException | DAOException e) {
            log.error(e);
        }
    }

    public void addRelationship(Entity selectedEntity, String relationshipData, String relationshipStartDate, String relationshipEndDate,
            RelationshipType selectedRelationship, boolean reversed) {
        List<Relationship> relationships = linkedRelationships.get(selectedEntity.getCurrentType());
        String relationshipStatus = "New";

        relationshipStatus = selectedEntity.getStatusProperty().getWert();

        Relationship rel = new Relationship();
        rel.setAdditionalData(relationshipData);
        rel.setBeginningDate(relationshipStartDate);
        rel.setEndDate(relationshipEndDate);
        rel.setDisplayName(selectedEntity.getEntityName());
        rel.setEntityName(selectedEntity.getCurrentType().getName());
        rel.setProcessId(String.valueOf(selectedEntity.getCurrentProcess().getId()));
        rel.setProcessStatus(relationshipStatus);
        rel.setType(selectedRelationship);
        rel.setVocabularyName(selectedRelationship.getVocabularyName());
        rel.setVocabularyUrl(selectedRelationship.getVocabularyUrl());

        MetadataGroup relationGroup = null;

        try {
            relationGroup = new MetadataGroup(prefs.getMetadataGroupTypeByName(configuration.getRelationshipMetadataName()));
            List<Metadata> mdl = relationGroup.getMetadataByType(configuration.getRelationshipEntityType());
            if (mdl != null && !mdl.isEmpty()) {
                mdl.get(0).setValue(rel.getEntityName());
            } else {
                Metadata md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipEntityType()));
                relationGroup.addMetadata(md);
                md.setValue(rel.getEntityName());
            }
            if (StringUtils.isNotBlank(relationshipStartDate)) {
                mdl = relationGroup.getMetadataByType(configuration.getRelationshipBeginningDate());
                if (mdl != null && !mdl.isEmpty()) {
                    mdl.get(0).setValue(relationshipStartDate);
                } else {
                    Metadata md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipBeginningDate()));
                    relationGroup.addMetadata(md);
                    md.setValue(relationshipStartDate);
                }
            }
            if (StringUtils.isNotBlank(relationshipEndDate)) {
                mdl = relationGroup.getMetadataByType(configuration.getRelationshipEndDate());
                if (mdl != null && !mdl.isEmpty()) {
                    mdl.get(0).setValue(relationshipEndDate);
                } else {
                    Metadata md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipEndDate()));
                    relationGroup.addMetadata(md);
                    md.setValue(relationshipEndDate);
                }
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

            mdl = relationGroup.getMetadataByType(configuration.getRelationshipType());
            Metadata md = null;
            if (mdl != null && !mdl.isEmpty()) {
                md = mdl.get(0);
            } else {
                md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipType()));
                relationGroup.addMetadata(md);
            }

            if (reversed && StringUtils.isNotBlank(selectedRelationship.getReversedRelationshipNameEn())) {
                rel.setReverse(true);
                md.setValue(selectedRelationship.getReversedRelationshipNameEn());
            } else {
                md.setValue(selectedRelationship.getRelationshipNameEn());
            }
            md.setAutorityFile(rel.getVocabularyName(), EntityConfig.vocabularyUrl, rel.getVocabularyUrl());

            if (StringUtils.isNotBlank(relationshipData)) {

                mdl = relationGroup.getMetadataByType(configuration.getRelationshipAdditionalData());
                if (mdl != null && !mdl.isEmpty()) {
                    mdl.get(0).setValue(relationshipData);
                } else {
                    md = new Metadata(prefs.getMetadataTypeByName(configuration.getRelationshipAdditionalData()));
                    relationGroup.addMetadata(md);
                    md.setValue(relationshipData);
                }
            }

            currentFileformat.getDigitalDocument().getLogicalDocStruct().addMetadataGroup(relationGroup);

        } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException | PreferencesException e) {
            log.error(e);
        }

        rel.setMetadataGroup(relationGroup);
        relationships.add(rel);

    }
}
