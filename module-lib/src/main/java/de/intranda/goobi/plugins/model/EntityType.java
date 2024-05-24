package de.intranda.goobi.plugins.model;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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
        name = other.getName();
        plural = other.getPlural();
        rulesetName = other.getRulesetName();
        color = other.getColor();
        icon = other.getIcon();
        identifyingMetadata = other.getIdentifyingMetadata();
        languageOrder = other.getLanguageOrder();
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        EntityType other = (EntityType) obj;
        if (color == null) {
            if (other.color != null) {
                return false;
            }
        } else if (!color.equals(other.color)) {
            return false;
        }
        if (icon == null) {
            if (other.icon != null) {
                return false;
            }
        } else if (!icon.equals(other.icon)) {
            return false;
        }
        if (identifyingMetadata == null) {
            if (other.identifyingMetadata != null) {
                return false;
            }
        } else if (!identifyingMetadata.equals(other.identifyingMetadata)) {
            return false;
        }
        if (languageOrder == null) {
            if (other.languageOrder != null) {
                return false;
            }
        } else if (!languageOrder.equals(other.languageOrder)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (plural == null) {
            if (other.plural != null) {
                return false;
            }
        } else if (!plural.equals(other.plural)) {
            return false;
        }
        if (rulesetName == null) {
            if (other.rulesetName != null) {
                return false;
            }
        } else if (!rulesetName.equals(other.rulesetName)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((color == null) ? 0 : color.hashCode());
        result = prime * result + ((icon == null) ? 0 : icon.hashCode());
        result = prime * result + ((identifyingMetadata == null) ? 0 : identifyingMetadata.hashCode());
        result = prime * result + ((languageOrder == null) ? 0 : languageOrder.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((plural == null) ? 0 : plural.hashCode());
        result = prime * result + ((rulesetName == null) ? 0 : rulesetName.hashCode());
        return result;
    }

}
