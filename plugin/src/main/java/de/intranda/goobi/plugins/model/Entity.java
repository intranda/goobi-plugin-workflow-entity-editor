package de.intranda.goobi.plugins.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;

import de.intranda.goobi.plugins.model.MetadataField.SourceField;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataGroupType;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
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

    private Map<EntityType, List<Relationship>> linkedRelationships;

    // list of all metadata fields
    private List<ConfiguredField> metadataFieldList = new ArrayList<>();

    public Entity(EntityConfig configuration, Process process) {
        this.configuration = configuration;
        this.currentProcess = process;
        try {
            prefs = currentProcess.getRegelsatz().getPreferences();
            currentFileformat = new MetsMods(prefs);
            currentFileformat.read(currentProcess.getMetadataFilePath());
        } catch (UGHException | IOException | InterruptedException | SwapException | DAOException e) {
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
                        // TODO type from allowed list
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

    public void removeRelationship(EntityType type, Relationship relationship) {
        linkedRelationships.get(type).remove(relationship);
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

}
