package de.intranda.goobi.plugins.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BreadcrumbItem {

    private String entityType;
    private String entityName;
    private int processId;

    // person: #df07b9
    // agency: #e81c0c
    // work: #900688
    // award: #05b8cd
    // event: #19b609
    private String color;

    //fa-user
    //fa-university
    //fa-picture-o
    //fa-trophy
    //fa-calendar
    private String icon;

}
