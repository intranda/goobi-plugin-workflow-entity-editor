package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import io.goobi.vocabulary.exchange.FieldDefinition;
import io.goobi.vocabulary.exchange.FieldInstance;
import io.goobi.vocabulary.exchange.TranslationInstance;
import io.goobi.vocabulary.exchange.VocabularyRecord;
import io.goobi.workflow.api.vocabulary.VocabularyAPIManager;
import io.goobi.workflow.api.vocabulary.helper.ExtendedVocabulary;
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
    @Setter
    private String labelPosition = "none"; //none, top, left

    /*
     * Define, which input type should be used. Allowed values are:
     * checkbox: Checkbox
     * input: single line input field
     * textarea: multi line input area
     * select: dropdown with defined values
     * vocabularyList: dropdown with values from vocabulary
     * vocabularySearch: read only field with search button to get data from vocabulary
     * publish: toggle button to mark field as publishable
     * source: linked source
     * fileupload: file upload button
     * date: date field incl. date picker
     * geonames
     */
    @Getter
    @NonNull
    private String fieldType;

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

    private VocabularyAPIManager vocabularyAPIManager = VocabularyAPIManager.getInstance();
    @Getter
    private String vocabularyName;
    @Getter
    private String vocabularyId;
    @Getter
    private String vocabularyUrl;

    @Getter
    private List<VocabularyEntry> vocabularyList;

    @Getter
    @Setter
    private List<String> searchFields;

    @Getter
    @Setter
    private List<String> displayFields;

    @Getter
    @Setter
    private List<String> valueList;

    @Getter
    @Setter
    private GenerationRule generationRule;

    @Getter
    @Setter
    private String defaultValue;

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

    @Getter
    @Setter
    private boolean showInSearch = false;

    // actual data
    @Getter
    @Setter
    private List<MetadataField> metadataList = new ArrayList<>();

    @Getter
    private List<ConfiguredField> subfieldList = new ArrayList<>(); // fields for group

    @Getter
    @Setter
    private Entity entity;

    public ConfiguredField(ConfiguredField other) {

        label = other.getLabel();

        labelPosition = other.getLabelPosition();

        fieldType = other.getFieldType();

        metadataName = other.getMetadataName();
        required = other.isRequired();
        readonly = other.isReadonly();
        repeatable = other.isRepeatable();
        source = other.isSource();

        vocabularyName = other.getVocabularyName();
        vocabularyId = other.getVocabularyId();
        vocabularyUrl = other.getVocabularyUrl();
        vocabularyList = other.getVocabularyList();

        searchFields = other.getSearchFields();

        displayFields = other.getDisplayFields();

        valueList = other.getValueList();

        generationRule = other.getGenerationRule();

        defaultValue = other.getDefaultValue();

        group = other.isGroup();

        showField = other.isShowField();
        valid = true;
        validationError = other.getValidationError();

        showInSearch = other.isShowInSearch();

        for (MetadataField mf : other.getMetadataList()) {
            metadataList.add(new MetadataField(mf));
        }

        for (ConfiguredField mf : other.getSubfieldList()) {
            subfieldList.add(new ConfiguredField(mf));
        }
    }

    public void addSubfield(ConfiguredField field) {
        subfieldList.add(field);
    }

    public void setVocabulary(String name, String id) {
        vocabularyName = name;
        vocabularyId = id;
        ExtendedVocabulary currentVocabulary = vocabularyAPIManager.vocabularies().findByName(vocabularyName);
        vocabularyUrl = currentVocabulary.getURI();
        List<FieldDefinition> fieldDefinitions = vocabularyAPIManager.vocabularySchemas().get(currentVocabulary.getSchemaId()).getDefinitions();
        long mainFieldId = fieldDefinitions.stream()
                .filter(d -> Boolean.TRUE.equals(d.getMainEntry()))
                .findFirst()
                .map(FieldDefinition::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Vocabulary \"" + vocabularyName + "\" has no main field specififed. This should not be possible..."));

        if ("vocabularyList".equals(fieldType)) {

            List<VocabularyRecord> recordList = vocabularyAPIManager.vocabularyRecords()
                    .listPlain(currentVocabulary.getId())
                    .getContent();

            vocabularyList = new ArrayList<>(recordList.size());

            for (VocabularyRecord vr : recordList) {
                VocabularyEntry ve = new VocabularyEntry();
                ve.setId(vr.getId());

                ve.setEntryUrl(vr.get_links().get("self").getHref());

                for (FieldInstance efi : vr.getFields()) {
                    if (efi.getDefinitionId().equals(mainFieldId)) {
                        List<TranslationInstance> translations = efi.getValues().get(0).getTranslations();

                        for (TranslationInstance ti : translations) {
                            if ("eng".equals(ti.getLanguage())) {
                                ve.setLabelEn(ti.getValue());
                                ve.setMainValue(ti.getValue());
                            } else if ("ger".equals(ti.getLanguage())) {
                                ve.setLabelDe(ti.getValue());
                            } else if ("fre".equals(ti.getLanguage())) {
                                ve.setLabelFr(ti.getValue());
                            }
                        }
                    }
                }
                vocabularyList.add(ve);
            }

            vocabularyList.sort((r1, r2) -> r1.getLabelEn().compareToIgnoreCase(r2.getLabelEn()));
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

                MetadataGroup grp = new MetadataGroup(other.getType());
                other.getParent().addMetadataGroup(grp);
                MetadataField field = new MetadataField();
                field.setConfigField(this);
                field.setGroup(grp);
                adMetadataField(field);
                for (ConfiguredField subfield : subfieldList) {
                    if (!subfield.isGroup()) {
                        Metadata otherMetadata = subfield.getMetadataList().get(0).getMetadata();

                        MetadataType metadataType = otherMetadata.getType();
                        Metadata metadata = new Metadata(metadataType);
                        if (StringUtils.isNotBlank(subfield.getDefaultValue())) {
                            metadata.setValue(subfield.getDefaultValue());
                        }
                        grp.addMetadata(metadata);
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
                if (StringUtils.isNotBlank(defaultValue)) {
                    metadata.setValue(defaultValue);
                }
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

    // only call it when new entity is loaded
    public void clearMetadata(Entity entity) {
        this.entity = entity;
        showField = false;
        metadataList.clear();
        for (ConfiguredField cf : subfieldList) {
            cf.clearMetadata(entity);
        }
    }

}
