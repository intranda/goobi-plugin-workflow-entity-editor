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
