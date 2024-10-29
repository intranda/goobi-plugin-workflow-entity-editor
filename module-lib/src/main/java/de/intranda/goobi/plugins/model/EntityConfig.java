package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;

import io.goobi.vocabulary.exchange.FieldDefinition;
import io.goobi.vocabulary.exchange.Vocabulary;
import io.goobi.workflow.api.vocabulary.VocabularyAPIManager;
import io.goobi.workflow.api.vocabulary.helper.ExtendedVocabulary;
import io.goobi.workflow.api.vocabulary.helper.ExtendedVocabularyRecord;
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
    private VocabularyAPIManager vocabularyAPIManager = VocabularyAPIManager.getInstance();
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

    @Getter
    private int processTemplateId;

    @Getter
    private String exportPluginName;

    @Getter
    private boolean updateProcessTitle = false;

    @Getter
    private String uploadFolderName;
    @Getter
    private String conversionFolderName;

    public EntityConfig(XMLConfiguration config, boolean extendedConfiguration) {

        processTemplateId = config.getInt("/global/processTemplateId");
        exportPluginName = config.getString("/global/exportPlugin", "intranda_export_luxArtistDictionary");

        updateProcessTitle = config.getBoolean("/global/updateProcessTitle", false);
        uploadFolderName = config.getString("/global/uploadFolderName", "media");
        conversionFolderName = config.getString("/global/conversionFolderName", null);

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

        List<HierarchicalConfiguration> configuredTypes = config.configurationsAt("/type");
        for (HierarchicalConfiguration type : configuredTypes) {
            String entityName = type.getString("@name");
            String namePlural = type.getString("@plural");
            String rulesetName = type.getString("@rulesetName");
            String entityColor = type.getString("/color");
            String entityIcon = type.getString("/icon");
            String metadataName = type.getString("/identifyingMetadata");
            String order = type.getString("/identifyingMetadata/@languageOrder");
            EntityType newType = new EntityType(entityName, namePlural, rulesetName, entityColor, entityIcon, metadataName);
            newType.setLanguageOrder(order);
            this.allTypes.add(newType);

            if (extendedConfiguration) {

                for (HierarchicalConfiguration field : type.configurationsAt("/displayMetadata/field")) {
                    ConfiguredField metadataField = extractField(field);
                    newType.addMetadataField(metadataField);
                }

                for (HierarchicalConfiguration field : type.configurationsAt("/relations/relation")) {
                    long id = field.getLong("@id", 0L);
                    boolean reverse = field.getBoolean("@reverse", false);
                    String destinationEntity = field.getString("@destinationEntity");
                    String sourceEntity = entityName;
                    if (id != 0) {
                        ExtendedVocabulary vocabulary = vocabularyAPIManager.vocabularies().get(id);
                        List<FieldDefinition> fieldDefinitions =
                                vocabularyAPIManager.vocabularySchemas().get(vocabulary.getSchemaId()).getDefinitions();

                        long relationshipTypeId = extractFieldId(vocabulary, fieldDefinitions, "Relationship type");
                        long reverseRelationshipTypeId = extractFieldId(vocabulary, fieldDefinitions, "Reverse relationship");
                        long dateBeginningAllowedId = extractFieldId(vocabulary, fieldDefinitions, "Date beginning allowed");
                        long dateEndAllowedId = extractFieldId(vocabulary, fieldDefinitions, "Date end allowed");
                        long additionalTextFieldAllowedId = extractFieldId(vocabulary, fieldDefinitions, "Additional text field allowed");

                        List<ExtendedVocabularyRecord> records = vocabularyAPIManager.vocabularyRecords()
                                .list(vocabulary.getId())
                                .all()
                                .request()
                                .getContent();
                        for (ExtendedVocabularyRecord rec : records) {
                            RelationshipType relationType = new RelationshipType();
                            relationType.setReversed(reverse);
                            relationType.setSourceType(sourceEntity);
                            relationType.setDestinationType(destinationEntity);

                            relationType.setVocabularyName(vocabulary.getName());
                            relationType.setVocabularyUrl(vocabulary.getURI());

                            // Ignore multi-values
                            rec.getFields()
                                    .stream()
                                    .filter(f -> f.getDefinitionId().equals(relationshipTypeId))
                                    .flatMap(f -> f.getValues().stream())
                                    .flatMap(v -> v.getTranslations().stream())
                                    .forEach(t -> {
                                        if (t.getLanguage() == null) {
                                            throw new IllegalStateException("No language specified, this should never happen!");
                                        }
                                        if (!reverse) {
                                            switch (t.getLanguage()) {
                                                case "eng":
                                                    relationType.setRelationshipNameEn(t.getValue());
                                                    break;
                                                case "ger":
                                                    relationType.setRelationshipNameDe(t.getValue());
                                                    break;
                                                case "fre":
                                                    relationType.setRelationshipNameFr(t.getValue());
                                                    break;
                                                default:
                                                    throw new IllegalArgumentException("Unknown language \"" + t.getLanguage() + "\"");
                                            }
                                        } else {
                                            switch (t.getLanguage()) {
                                                case "eng":
                                                    relationType.setReversedRelationshipNameEn(t.getValue());
                                                    break;
                                                case "ger":
                                                    relationType.setReversedRelationshipNameDe(t.getValue());
                                                    break;
                                                case "fre":
                                                    relationType.setReversedRelationshipNameFr(t.getValue());
                                                    break;
                                                default:
                                                    throw new IllegalArgumentException("Unknown language \"" + t.getLanguage() + "\"");
                                            }
                                        }
                                    });
                            rec.getFields()
                                    .stream()
                                    .filter(f -> f.getDefinitionId().equals(reverseRelationshipTypeId))
                                    .flatMap(f -> f.getValues().stream())
                                    .flatMap(v -> v.getTranslations().stream())
                                    .forEach(t -> {
                                        if (t.getLanguage() == null) {
                                            throw new IllegalStateException("No language specified, this should never happen!");
                                        }
                                        if (reverse) {
                                            switch (t.getLanguage()) {
                                                case "eng":
                                                    relationType.setRelationshipNameEn(t.getValue());
                                                    break;
                                                case "ger":
                                                    relationType.setRelationshipNameDe(t.getValue());
                                                    break;
                                                case "fre":
                                                    relationType.setRelationshipNameFr(t.getValue());
                                                    break;
                                                default:
                                                    throw new IllegalArgumentException("Unknown language \"" + t.getLanguage() + "\"");
                                            }
                                        } else {
                                            switch (t.getLanguage()) {
                                                case "eng":
                                                    relationType.setReversedRelationshipNameEn(t.getValue());
                                                    break;
                                                case "ger":
                                                    relationType.setReversedRelationshipNameDe(t.getValue());
                                                    break;
                                                case "fre":
                                                    relationType.setReversedRelationshipNameFr(t.getValue());
                                                    break;
                                                default:
                                                    throw new IllegalArgumentException("Unknown language \"" + t.getLanguage() + "\"");
                                            }
                                        }
                                    });
                            rec.getFields()
                                    .stream()
                                    .filter(f -> f.getDefinitionId().equals(dateBeginningAllowedId))
                                    .flatMap(f -> f.getValues().stream())
                                    .flatMap(v -> v.getTranslations().stream())
                                    .forEach(t -> {
                                        if (t.getLanguage() != null) {
                                            throw new IllegalStateException("Language specification not allowed here, this should never happen!");
                                        }
                                        if ("yes".equals(t.getValue())) {
                                            relationType.setDisplayStartDate(true);
                                        }
                                    });
                            rec.getFields()
                                    .stream()
                                    .filter(f -> f.getDefinitionId().equals(dateEndAllowedId))
                                    .flatMap(f -> f.getValues().stream())
                                    .flatMap(v -> v.getTranslations().stream())
                                    .forEach(t -> {
                                        if (t.getLanguage() != null) {
                                            throw new IllegalStateException("Language specification not allowed here, this should never happen!");
                                        }
                                        if ("yes".equals(t.getValue())) {
                                            relationType.setDisplayEndDate(true);
                                        }
                                    });
                            rec.getFields()
                                    .stream()
                                    .filter(f -> f.getDefinitionId().equals(additionalTextFieldAllowedId))
                                    .flatMap(f -> f.getValues().stream())
                                    .flatMap(v -> v.getTranslations().stream())
                                    .forEach(t -> {
                                        if (t.getLanguage() != null) {
                                            throw new IllegalStateException("Language specification not allowed here, this should never happen!");
                                        }
                                        if ("yes".equals(t.getValue())) {
                                            relationType.setDisplayAdditionalData(true);
                                        }
                                    });

                            if (StringUtils.isNotBlank(relationType.getRelationshipNameEn())) {
                                newType.addRelationshipType(relationType);
                            }
                        }

                    }
                }
            }

        }
    }

    private long extractFieldId(Vocabulary vocabulary, List<FieldDefinition> fieldDefinitions, String fieldName) {
        return fieldDefinitions.stream()
                .filter(d -> fieldName.equals(d.getName()))
                .map(FieldDefinition::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Vocabulary \"" + vocabulary.getName() + "\" [" + vocabulary.getId()
                        + "] does not contain required field \"" + fieldName + "\""));
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

        boolean showInSearch = field.getBoolean("@showInSearch", false);
        metadataField.setShowInSearch(showInSearch);

        String defaultValue = field.getString("@defaultValue", null);
        metadataField.setDefaultValue(defaultValue);

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
        } else if ("geonames".equals(fieldType)) {
            List<String> searchFields = Arrays.asList(field.getStringArray("@source"));
            metadataField.setSearchFields(searchFields);
        }
        metadataField.setGenerationRule(new GenerationRule(field.getString("/rule"), field.getString("/rule/@numberFormat")));

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

    public EntityConfig(EntityConfig other) {

        sourceSearchFields = other.getSourceSearchFields();

        sourceDisplayFields = other.getSourceDisplayFields();

        sourceVocabularyName = other.getSourceVocabularyName();

        sourceVocabularyId = other.getSourceVocabularyId();

        sourceNameFields = other.getSourceDisplayFields();

        sourceUrlFields = other.getSourceUrlFields();

        sourceTypeFields = other.getSourceTypeFields();

        for (EntityType t : other.getAllTypes()) {
            allTypes.add(new EntityType(t));
        }

        relationshipMetadataName = other.relationshipMetadataName;

        relationshipEntityType = other.relationshipEntityType;

        relationshipBeginningDate = other.relationshipBeginningDate;

        relationshipEndDate = other.relationshipEndDate;

        relationshipAdditionalData = other.relationshipAdditionalData;

        relationshipProcessId = other.relationshipProcessId;

        relationshipDisplayName = other.relationshipDisplayName;

        relationshipType = other.relationshipType;

        processTemplateId = other.processTemplateId;

        updateProcessTitle = other.updateProcessTitle;

        exportPluginName = other.exportPluginName;
        uploadFolderName = other.uploadFolderName;
        conversionFolderName = other.conversionFolderName;
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
