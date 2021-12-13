package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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

    @Data
    @RequiredArgsConstructor
    public class EntityType {
        @NonNull
        private String name;
        @NonNull
        private String color;
        @NonNull
        private String icon;
        @NonNull

        private String identifyingMetadata;
        private String languageOrder;

        private String scrollPosition;

        private List<ConfiguredField> configuredFields = new ArrayList<>();

        public void addMetadataField(ConfiguredField field) {
            configuredFields.add(field);
        }

        public void setScrollPosition(String pos) {
            System.out.println(pos);
            scrollPosition = pos;
        }
    }
}
