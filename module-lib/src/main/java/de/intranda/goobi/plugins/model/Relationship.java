package de.intranda.goobi.plugins.model;

import lombok.Data;
import org.apache.commons.lang.StringUtils;
import ugh.dl.MetadataGroup;

import java.util.Locale;

@Data
public class Relationship {

    // read from metadata file
    private String entityName;
    private String beginningDate;
    private String endDate;

    private String additionalData;
    private String processId;
    private String displayName;

    private RelationshipType type;
    private String vocabularyName;
    private String vocabularyUrl;
    private String valueUrl;

    private String processStatus = "New";

    private boolean showDetails;
    private boolean reverse;

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
        String label;
        switch (lang.getLanguage()) {
            case "fr":
                if (reverse && StringUtils.isNotBlank(type.getReversedRelationshipNameFr())) {
                    label = type.getReversedRelationshipNameFr();
                } else {
                    label = type.getRelationshipNameFr();
                }
                break;
            case "en":
                if (reverse && StringUtils.isNotBlank(type.getReversedRelationshipNameEn())) {
                    label = type.getReversedRelationshipNameEn();

                } else {
                    label = type.getRelationshipNameEn();
                }

                break;
            default:
                if (reverse && StringUtils.isNotBlank(type.getReversedRelationshipNameDe())) {
                    label = type.getReversedRelationshipNameDe();

                } else {
                    label = type.getRelationshipNameDe();
                }

                break;
        }
        return label;

    }
}
