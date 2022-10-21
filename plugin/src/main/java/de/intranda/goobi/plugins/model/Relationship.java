package de.intranda.goobi.plugins.model;

import lombok.Data;
import ugh.dl.MetadataGroup;

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

    private String processStatus = "New";

    private boolean showDetails;
    private boolean reverse;

    private MetadataGroup metadataGroup;



}
