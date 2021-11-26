package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.lang.StringUtils;
import org.goobi.vocabulary.Field;
import org.goobi.vocabulary.VocabRecord;
import org.goobi.vocabulary.Vocabulary;

import de.sub.goobi.persistence.managers.VocabularyManager;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataType;
import ugh.exceptions.UGHException;

@RequiredArgsConstructor
@Log4j2
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

    /** defines if the field is displayed as input field (true) or badge (false, default), affects only visible metadata */
    @Getter
    @Setter
    private boolean showField = false;
    @Getter
    /** contains the result of the validation */
    private boolean valid = true;
    @Getter
    /** contains a human readable error text */
    private String validationError;

    // actual data
    @Getter
    private List<MetadataField> metadataList = new ArrayList<>();

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

    public void adMetadataField(MetadataField metadataField) {
        metadataList.add(metadataField);
    }

    public void addValue() {

        if (metadataList == null) {
            metadataList = new ArrayList<>();
        }
        if (group) {
            try {
                MetadataGroup other = metadataList.get(0).getGroup();

                MetadataGroup group = new MetadataGroup(other.getType());
                other.getParent().addMetadataGroup(group);
                MetadataField field = new MetadataField();
                field.setConfigField(this);
                field.setGroup(group);
                adMetadataField(field);
                for (ConfiguredField subfield : subfieldList) {
                    if (!subfield.isGroup()) {
                        Metadata otherMetadata = subfield.getMetadataList().get(0).getMetadata();

                        MetadataType metadataType = otherMetadata.getType();
                        Metadata metadata = new Metadata(metadataType);
                        group.addMetadata(metadata);
                        MetadataField sub = new MetadataField();
                        sub.setConfigField(subfield);
                        sub.setMetadata(metadata);
                        field.addSubField(sub);
                    }
                }
            } catch (UGHException e) {
                log.error(e);
            }
        } else {
            try {
                Metadata other = metadataList.get(0).getMetadata();
                Metadata metadata = new Metadata(other.getType());
                other.getParent().addMetadata(metadata);
                MetadataField field = new MetadataField();
                field.setConfigField(this);
                field.setMetadata(metadata);
                adMetadataField(field);
            } catch (UGHException e) {
                log.error(e);
            }

        }
    }

    public boolean isFilled() {
        if (metadataList == null || metadataList.isEmpty()) {
            return false;
        }
        if (group) {
            for (MetadataField grp : metadataList) {
                for (MetadataField val : grp.getSubfields()) {
                    if (!"publish".equals(val.getConfigField().getFieldType()) && !val.getConfigField().isGroup()
                            && StringUtils.isNotBlank(val.getMetadata().getValue())) {
                        return true;
                    }
                }
            }
        } else {
            for (MetadataField val : metadataList) {
                if (StringUtils.isNotBlank(val.getMetadata().getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void clearMetadata( ) {
        metadataList.clear();
        for (ConfiguredField cf: subfieldList ) {
            cf.clearMetadata();
        }
    }
}
