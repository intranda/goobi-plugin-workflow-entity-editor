package de.intranda.goobi.plugins.model;

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

}
