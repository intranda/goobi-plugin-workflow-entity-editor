package de.intranda.goobi.plugins.model;

import java.util.Locale;

import lombok.Data;

@Data
public class RelationshipType {

    private String sourceType;

    private String destinationType;

    private String relationshipNameEn;
    private String relationshipNameDe;
    private String relationshipNameFr;

    private String vocabularyName;
    private String vocabularyUrl;
    private String valueUrl;

    private boolean displayTierField = false;
    private boolean displayStartDate = false;
    private boolean displayEndDate = false;

    private boolean displayAdditionalData = false;

    private String reversedRelationshipNameEn;
    private String reversedRelationshipNameDe;
    private String reversedRelationshipNameFr;

    public String getLabel(Locale lang) {
        String label;
        switch (lang.getLanguage()) {
            case "fr":
                label = relationshipNameFr;

                break;
            case "en":

                label = relationshipNameEn;

                break;
            default:
                label = relationshipNameDe;

                break;
        }
        return label;

    }

}
