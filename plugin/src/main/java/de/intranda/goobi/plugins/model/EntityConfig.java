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

public class EntityConfig {

    public static String vocabularyUrl;

    @Getter
    private List<EntityType> allTypes = new ArrayList<>();

    public EntityConfig(XMLConfiguration config) {

        vocabularyUrl = config.getString("/global/vocabularyServerUrl");

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

        private List<ConfiguredField> configuredFields = new ArrayList<>();

        public void addMetadataField(ConfiguredField field) {
            configuredFields.add(field);
        }
    }
}
