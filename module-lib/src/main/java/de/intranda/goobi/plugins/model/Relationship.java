package de.intranda.goobi.plugins.model;

import java.util.Locale;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import ugh.dl.MetadataGroup;

@Data
public class Relationship {

    private static final PolicyFactory SANITIZER = new HtmlPolicyBuilder()
            .allowElements("b", "i", "em", "strong", "br", "p", "ul", "ol", "li")
            .toFactory();

    // read from metadata file
    private String entityName;
    private String beginningDate;
    private String endDate;

    @Getter(AccessLevel.NONE)
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

    public String getAdditionalData() {
        return additionalData == null ? null : SANITIZER.sanitize(additionalData);
    }

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
