package de.intranda.goobi.plugins.model;

import java.util.Locale;

import lombok.Data;
import ugh.dl.MetadataGroup;

@Data
public class Relationship {

    // read from metadata file
    private String entityName;
    private String beginningDate;
    private String endDate;

    private String additionalData;
    private String sourceType;
    private String awardTier;
    private String awardTierUri;

    private String processId;
    private String displayName;

    private RelationshipType type;
    private String vocabularyName;
    private String vocabularyUrl;
    private String valueUrl;

    private String processStatus = "New";

    private boolean showDetails;

    private MetadataGroup metadataGroup;

    // Makes sure that the vocabulary references are updated when the type changes
    public void setType(RelationshipType type) {
        this.type = type;
        if (type == null) {
            return;
        }

        this.vocabularyName = type.getVocabularyName();
        this.vocabularyUrl = type.getVocabularyUrl();
        this.valueUrl = type.getValueUrl();
    }

    public String getLabel(Locale lang) {
        return switch (lang.getLanguage()) {
            case "de" -> type.getRelationshipNameDe();
            case "fr" -> type.getRelationshipNameFr();
            default -> type.getRelationshipNameEn();
        };
    }
}
