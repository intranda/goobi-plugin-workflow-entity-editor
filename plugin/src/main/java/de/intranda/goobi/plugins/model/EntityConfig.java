package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;
import org.goobi.vocabulary.Field;
import org.goobi.vocabulary.VocabRecord;
import org.goobi.vocabulary.Vocabulary;

import de.sub.goobi.persistence.managers.VocabularyManager;
import lombok.Getter;
import lombok.Setter;

public class EntityConfig {

    public static String vocabularyUrl;

    @Getter
    @Setter
    private List<String> sourceSearchFields;

    @Getter
    @Setter
    private List<String> sourceDisplayFields;
    @Getter
    @Setter
    private String sourceVocabularyName;
    @Getter
    @Setter
    private int sourceVocabularyId;
    @Getter
    private List<String> sourceNameFields;
    @Getter
    private List<String> sourceUrlFields;
    @Getter
    private List<String> sourceTypeFields;

    @Getter
    private List<EntityType> allTypes = new ArrayList<>();

    // relationships
    @Getter
    private String relationshipMetadataName;
    @Getter
    private String relationshipEntityType;
    @Getter
    private String relationshipBeginningDate;
    @Getter
    private String relationshipEndDate;

    @Getter
    private String relationshipAdditionalData;
    @Getter
    private String relationshipProcessId;
    @Getter
    private String relationshipDisplayName;

    @Getter
    private String relationshipType;

    public EntityConfig(XMLConfiguration config) {

        // data for vocabulary search
        vocabularyUrl = config.getString("/global/vocabularyServerUrl");
        sourceVocabularyId = config.getInt("/global/sources/vocabulary/@id", 0);
        sourceVocabularyName = config.getString("/global/sources/vocabulary/@name", "");

        // data for sources
        sourceSearchFields = Arrays.asList(config.getStringArray("/global/sources/vocabulary/@searchfields"));
        sourceDisplayFields = Arrays.asList(config.getStringArray("/global/sources/vocabulary/@displayfields"));
        sourceNameFields = Arrays.asList(config.getStringArray("/global/sources/vocabulary/@nameField"));
        sourceUrlFields = Arrays.asList(config.getStringArray("/global/sources/vocabulary/@urlField"));
        sourceTypeFields = Arrays.asList(config.getStringArray("/global/sources/vocabulary/@typeField"));

        // relations between entities
        relationshipMetadataName = config.getString("/global/relations/metadataName", "");
        relationshipEntityType = config.getString("/global/relations/entityType", "");
        relationshipBeginningDate = config.getString("/global/relations/beginningDate", "");
        relationshipEndDate = config.getString("/global/relations/endDate", "");
        relationshipAdditionalData = config.getString("/global/relations/additionalData", "");
        relationshipProcessId = config.getString("/global/relations/processId", "");
        relationshipDisplayName = config.getString("/global/relations/displayName", "");
        relationshipType = config.getString("/global/relations/type", "");

        List<HierarchicalConfiguration> allTypes = config.configurationsAt("/type");
        for (HierarchicalConfiguration type : allTypes) {
            String entityName = type.getString("@name");
            String entityColor = type.getString("/color");
            String entityIcon = type.getString("/icon");
            String metadataName = type.getString("/identifyingMetadata");
            String order = type.getString("/identifyingMetadata/@languageOrder");
            EntityType newType = new EntityType(entityName, entityColor, entityIcon, metadataName);
            newType.setLanguageOrder(order);
            this.allTypes.add(newType);

            for (HierarchicalConfiguration field : type.configurationsAt("/displayMetadata/field")) {
                ConfiguredField metadataField = extractField(field);
                newType.addMetadataField(metadataField);
            }

            for (HierarchicalConfiguration field : type.configurationsAt("/relations/relation")) {
                int id = field.getInt("@id", 0);
                boolean reverse = field.getBoolean("@reverse", false);
                String destinationEntity = field.getString("@destinationEntity");
                String sourceEntity = entityName;
                if (id != 0) {
                    Vocabulary v = VocabularyManager.getVocabularyById(id);
                    VocabularyManager.getAllRecords(v);
                    for (VocabRecord record : v.getRecords()) {
                        RelationshipType relationType = new RelationshipType();
                        relationType.setReversed(reverse);
                        relationType.setSourceType(sourceEntity);
                        relationType.setDestinationType(destinationEntity);

                        for (Field f : record.getFields()) {
                            switch (f.getDefinition().getLabel()) {
                                case "Relationship type":
                                    if (!reverse) {
                                        if ("eng".equals(f.getLanguage())) {
                                            relationType.setRelationshipNameEn(f.getValue());
                                        } else if ("ger".equals(f.getLanguage())) {
                                            relationType.setRelationshipNameDe(f.getValue());
                                        } else if ("fre".equals(f.getLanguage())) {
                                            relationType.setRelationshipNameFr(f.getValue());
                                        }
                                    }
                                    break;
                                case "Reverse relationship":
                                    if (reverse && StringUtils.isNotBlank(f.getValue())) {
                                        if ("eng".equals(f.getLanguage())) {
                                            relationType.setRelationshipNameEn(f.getValue());
                                        } else if ("ger".equals(f.getLanguage())) {
                                            relationType.setRelationshipNameDe(f.getValue());
                                        } else if ("fre".equals(f.getLanguage())) {
                                            relationType.setRelationshipNameFr(f.getValue());
                                        }
                                    }
                                    break;

                                case "Date beginning allowed":
                                    if ("yes".equals(f.getValue())) {
                                        relationType.setDisplayStartDate(true);
                                    }
                                    break;
                                case "Date end allowed":
                                    if ("yes".equals(f.getValue())) {
                                        relationType.setDisplayEndDate(true);
                                    }
                                    break;
                                case "Additional text field allowed":
                                    if ("yes".equals(f.getValue())) {
                                        relationType.setDisplayAdditionalData(true);
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                        if (StringUtils.isNotBlank(relationType.getRelationshipNameEn())) {
                            newType.addRelationshipType(relationType);
                        }
                    }

                }

            }

        }
    }

    private ConfiguredField extractField(HierarchicalConfiguration field) {
        String label = field.getString("@label");
        String mtadataName = field.getString("@metadata");
        String fieldType = field.getString("@type", "input");
        ConfiguredField metadataField = new ConfiguredField(label, fieldType, mtadataName);

        String labelPosition = field.getString("@labelPosition", "none");
        metadataField.setLabelPosition(labelPosition);

        boolean required = field.getBoolean("@required", false);
        metadataField.setRequired(required);

        boolean readonly = field.getBoolean("@readonly", false);
        metadataField.setReadonly(readonly);

        boolean repeatable = field.getBoolean("@repeatable", false);
        metadataField.setRepeatable(repeatable);

        if ("vocabularyList".equals(fieldType)) {
            String vocabularyName = field.getString("/vocabulary/@name");
            String vocabularyId = field.getString("/vocabulary/@id");
            metadataField.setVocabulary(vocabularyName, vocabularyId);
        } else if ("select".equals(fieldType)) {
            metadataField.setValueList(Arrays.asList(field.getStringArray("/value")));
        } else if ("vocabularySearch".equals(fieldType)) {
            String vocabularyName = field.getString("/vocabulary/@name");
            String vocabularyId = field.getString("/vocabulary/@id");
            metadataField.setVocabulary(vocabularyName, vocabularyId);
            List<String> searchFields = Arrays.asList(field.getStringArray("/vocabulary/@searchfields"));
            List<String> displayFields = Arrays.asList(field.getStringArray("/vocabulary/@displayfields"));
            metadataField.setSearchFields(searchFields);
            metadataField.setDisplayFields(displayFields);
        }
        metadataField.setGenerationRule(field.getString("/rule"));

        boolean group = field.getBoolean("@group", false);
        metadataField.setGroup(group);
        if (group) {
            for (HierarchicalConfiguration subfield : field.configurationsAt("/field")) {
                ConfiguredField mf = extractField(subfield);
                metadataField.addSubfield(mf);
            }
        }

        return metadataField;
    }

    public EntityType getTypeByName(String name) {
        for (EntityType et : allTypes) {
            if (et.getName().equals(name)) {
                return et;
            }
        }
        return null;
    }



}
