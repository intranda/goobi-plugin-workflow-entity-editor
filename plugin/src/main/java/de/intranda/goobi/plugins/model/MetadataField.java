package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.lang.StringUtils;

import lombok.Data;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;

@Data
public class MetadataField {

    private ConfiguredField configField;

    private Metadata metadata;

    private MetadataGroup group; // null if no group is available

    private List<MetadataField> subfields = new ArrayList<>(); // items for metadata in group

    // boolean values

    public boolean isBooleanValue() {
        if (StringUtils.isBlank(metadata.getValue())) {
            return false;
        }
        return "Y".equals(metadata.getValue());
    }

    public void setBooleanValue(boolean val) {
        if (val) {
            metadata.setValue("Y");
        } else {
            metadata.setValue("N");
        }
    }

    // vocabulary dropdown

    public void setVocabularyValue(String value) {
        if (StringUtils.isNotBlank(value)) {
            for (SelectItem item : configField.getVocabularyList()) {
                if (value.equals(item.getValue())) {
                    metadata.setValue(item.getLabel());
                    metadata.setAutorityFile(configField.getVocabularyName(), configField.getVocabularyUrl(),
                            configField.getVocabularyUrl() + "/" + value);
                }
            }
        }
    }

    public String getVocabularyValue() {
        String label = metadata.getValue();
        if (StringUtils.isNotBlank(label)) {
            for (SelectItem item : configField.getVocabularyList()) {
                if (label.equals(item.getLabel())) {
                    return (String) item.getValue();
                }
            }
        }
        return null;
    }

}
