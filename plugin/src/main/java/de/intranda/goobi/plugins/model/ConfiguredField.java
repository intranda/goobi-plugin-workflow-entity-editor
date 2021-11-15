package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.faces.model.SelectItem;

import org.goobi.vocabulary.Field;
import org.goobi.vocabulary.VocabRecord;
import org.goobi.vocabulary.Vocabulary;

import de.sub.goobi.persistence.managers.VocabularyManager;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class ConfiguredField {

    @Getter
    @NonNull
    private String label; // displayed name
    @Getter
    @NonNull
    private String fieldType; // type, input, select, ....
    @Getter
    @NonNull
    private String metadataName; // metadata or group name
    @Getter
    @Setter
    private boolean required = false;
    @Getter
    @Setter
    private boolean readonly = false;
    @Getter
    @Setter
    private boolean repeatable = false;
    @Getter
    @Setter
    private boolean source = false;

    @Getter
    private String vocabularyName;
    @Getter
    private String vocabularyId;
    @Getter
    private String vocabularyUrl;
    @Getter
    private List<SelectItem> vocabularyList;

    @Getter
    @Setter
    private List<String> valueList;

    @Getter
    @Setter
    private String generationRule;
    @Getter
    @Setter
    private boolean group = false; // metadata or group

    // actual data
    //    @Getter
    //    @Setter
    //    private Metadata metadata;
    //    @Getter
    //    @Setter
    //    private MetadataGroup group;
    @Getter
    private List<ConfiguredField> subfieldList = new ArrayList<>(); // fields for group

    public void addSubfield(ConfiguredField field) {
        subfieldList.add(field);
    }

    public void setVocabulary(String name, String id) {
        vocabularyName = name;
        vocabularyId = id;
        Vocabulary currentVocabulary = VocabularyManager.getVocabularyByTitle(vocabularyName);
        vocabularyUrl = EntityConfig.vocabularyUrl + currentVocabulary.getId();
        if (currentVocabulary != null) {
            VocabularyManager.getAllRecords(currentVocabulary);
            List<VocabRecord> recordList = currentVocabulary.getRecords();
            Collections.sort(recordList);
            vocabularyList = new ArrayList<>(recordList.size());
            if (currentVocabulary != null && currentVocabulary.getId() != null) {
                for (VocabRecord vr : recordList) {
                    for (Field f : vr.getFields()) {
                        if (f.getDefinition().isMainEntry()) {
                            vocabularyList.add(new SelectItem(String.valueOf(vr.getId()), f.getValue()));
                            break;
                        }
                    }
                }
            }
        }
    }



}
