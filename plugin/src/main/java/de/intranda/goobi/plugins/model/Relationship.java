package de.intranda.goobi.plugins.model;

import lombok.Data;

@Data
public class Relationship {


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

    // TODO how to define process status?
    private String processStatus = "Published";


    private boolean showDetails;
}
