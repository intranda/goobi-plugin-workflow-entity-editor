package de.intranda.goobi.plugins.model;

import lombok.Data;
import ugh.dl.MetadataGroup;

@Data
public class Relationship {

    // TODO change type to Relationship object?
    // TODO change processStatus to enumeration

    // read from metadata file
    private String entityName;
    private String beginningDate;
    private String endDate;

    private String additionalData;
    private String processId;
    private String displayName;

    private String type;
    private String vocabularyName;
    private String vocabularyUrl;

    private String processStatus = "New";

    private boolean showDetails;

    private MetadataGroup metadataGroup;
}
