package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

public class EntityConfig {

    @Getter
    private List<EntityType> allTypes = new ArrayList<>();

    public EntityConfig(XMLConfiguration config) {

        List<HierarchicalConfiguration> allTypes = config.configurationsAt("/type");
        for (HierarchicalConfiguration type : allTypes) {
            String entityName = type.getString("@name");
            String entityColor = type.getString("/color");
            String entityIcon = type.getString("/icon");
            String metadataName = type.getString("/identifyingMetadata");
            this.allTypes.add(new EntityType(entityName, entityColor, entityIcon, metadataName));

        }
    }

    public EntityType getTypeByName(String name) {
        for (EntityType et : allTypes) {
            if (et.getName().equals(name)) {
                return et;
            }
        }
        return null;
    }

    @Data
    @AllArgsConstructor
    public class EntityType {

        private String name;
        private String color;
        private String icon;
        private String identifyingMetadata;
    }

}
