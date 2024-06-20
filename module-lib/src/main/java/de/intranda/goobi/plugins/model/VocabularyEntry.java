package de.intranda.goobi.plugins.model;

import lombok.Data;
import org.apache.commons.lang.StringUtils;

import java.util.Locale;

@Data
public class VocabularyEntry {

    private String mainValue;

    private String labelDe;
    private String labelEn;
    private String labelFr;

    private long id;
    private String entryUrl;

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
