package de.intranda.goobi.plugins.model;

import java.util.Locale;

import org.apache.commons.lang.StringUtils;

import lombok.Data;

@Data
public class VocabularyEntry {

    private String mainValue;

    private String labelDe;
    private String labelEn;
    private String labelFr;

    public String getLabel(Locale lang) {
        String label;
        switch (lang.getLanguage()) {
            case "fr":
                label = labelFr;
                break;
            case "en":
                label = labelEn;
                break;
            default:
                label = labelDe;
                break;
        }
        return StringUtils.isBlank(label) ? mainValue : label;

    }

}
