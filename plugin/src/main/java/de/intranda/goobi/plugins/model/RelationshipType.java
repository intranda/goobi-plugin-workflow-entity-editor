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

    private boolean displayStartDate = false;
    private boolean displayEndDate = false;

    private boolean displayAdditionalData = false;

    private boolean reversed = false;

    private String reversedRelationshipNameEn;
    private String reversedRelationshipNameDe;
    private String reversedRelationshipNameFr;


    public String getLabel(Locale lang) {
        String label;
        switch (lang.getLanguage()) {
            case "fr":
                if (reversed) {
                    label = reversedRelationshipNameFr;
                } else {
                    label=relationshipNameFr;
                }
                break;
            case "en":
                if (reversed) {
                    label = reversedRelationshipNameEn;
                } else {
                    label=relationshipNameEn;
                }
                break;
            default:
                if (reversed) {
                    label = reversedRelationshipNameDe;
                } else {
                    label=relationshipNameDe;
                }
                break;
        }
        return label;

    }

}
