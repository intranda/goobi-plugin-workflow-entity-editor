package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class EntityType {
    @NonNull
    private String name;
    @NonNull
    private String plural;
    @NonNull
    private String rulesetName;

    @NonNull
    private String color;

    @NonNull
    private String icon;

    @NonNull
    private String identifyingMetadata;

    private String languageOrder;

    private String scrollPosition;

    private List<ConfiguredField> configuredFields = new ArrayList<>();

    private List<RelationshipType> configuredRelations = new ArrayList<>();

    private String searchValue;

    public EntityType(EntityType other) {
        name=other.getName();
        plural = other.getPlural();
        rulesetName = other.getRulesetName();
        color = other.getColor();
        icon = other.getIcon();
        identifyingMetadata = other.getIdentifyingMetadata();
        languageOrder=other.getLanguageOrder();
        for (ConfiguredField cf : other.getConfiguredFields()) {
            configuredFields.add(new ConfiguredField(cf));
        }
        configuredRelations = other.getConfiguredRelations();

    }


    private boolean showLinkedContent = true;

    public void addMetadataField(ConfiguredField field) {
        configuredFields.add(field);
    }

    public void addRelationshipType(RelationshipType field) {
        configuredRelations.add(field);
    }

    public void setScrollPosition(String pos) {
        scrollPosition = pos;
    }
}
