package de.intranda.goobi.plugins.model;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.servlet.http.Part;

import org.apache.commons.lang.StringUtils;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.validator.EDTFValidator;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.UGHException;

@Data
@Log4j2
public class MetadataField {

    private ConfiguredField configField;

    private Metadata metadata;

    private MetadataGroup group; // null if no group is available

    private List<MetadataField> subfields = new ArrayList<>(); // items for metadata in group

    private MetadataField publishField;

    private boolean valid;
    private String validationErrorMessage;

    private boolean allowSources;

    private List<SourceField> sources = new ArrayList<>();

    public void addSource(SourceField field, MetadataGroup grp) {
        sources.add(field);
        if (group != null && grp != null) {
            try {
                group.addMetadataGroup(grp);
            } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
                log.error(e);
            }
        }
    }

    public void removeSource(SourceField field) {
        sources.remove(field);
        if (group != null) {
            MetadataGroup toRemove = null;
            for (MetadataGroup mg : group.getAllMetadataGroups()) {
                if (mg.getType().getName().equals("Source")) {
                    for (Metadata md : mg.getMetadataByType("SourceID")) {
                        if (md.getValue().equals(field.getSourceId())) {
                            toRemove = mg;
                            break;
                        }
                    }
                }
            }
            if (toRemove != null) {
                group.removeMetadataGroup(toRemove, true);
            }
        }
    }

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

    public void addSubField(MetadataField field) {
        subfields.add(field);
    }

    public boolean isDisplayPublishButton() {
        if (publishField != null) {
            return true;
        }

        if (configField.isGroup()) {
            for (MetadataField mf : subfields) {
                if ("publish".equals(mf.getConfigField().getFieldType())) {
                    publishField = mf;
                    return true;
                }
            }
        }
        return false;
    }

    public void dateValidator(FacesContext context, UIComponent component, Object value) {
        valid = true;
        validationErrorMessage = null;
        if (configField.isRequired() && isEmpty(value)) {
            valid = false;
            validationErrorMessage = "Field is required";
        } else {
            String dateValue = (String) value;
            EDTFValidator validator = new EDTFValidator();
            if (validator.isValid(dateValue)) {
                return;
            } else {
                valid = false;
                validationErrorMessage = "Invalid date format. Dates must comply with EDTF specifications.";
            }
        }
    }

    public void requiredValidator(FacesContext context, UIComponent component, Object value) {
        valid = true;
        validationErrorMessage = null;
        if (configField.isRequired() && isEmpty(value)) {
            valid = false;
            validationErrorMessage = "Field is required";
        }
        return;
    }

    private boolean isEmpty(Object value) {
        String fieldContents = (String) value;
        if (value == null || StringUtils.isBlank(fieldContents)) {
            return true;
        } else {
            return false;
        }
    }

    @Getter
    @Setter
    private Part uploadedFile = null;

    /**
     * File upload with binary copying.
     */
    public void uploadFile() {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            if (this.uploadedFile == null) {
                Helper.setFehlerMeldung("noFileSelected");
                return;
            }

            String basename = getFileName(this.uploadedFile);
            if (basename.startsWith(".")) {
                basename = basename.substring(1);
            }
            if (basename.contains("/")) {
                basename = basename.substring(basename.lastIndexOf("/") + 1);
            }
            if (basename.contains("\\")) {
                basename = basename.substring(basename.lastIndexOf("\\") + 1);
            }

            String filename = configField.getEntity().getCurrentProcess().getImagesOrigDirectory(false) + basename;

            inputStream = this.uploadedFile.getInputStream();
            outputStream = new FileOutputStream(filename);

            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
            metadata.setValue(filename);

            try {
                Prefs prefs = configField.getEntity().getPrefs();
                DigitalDocument dd = configField.getEntity().getCurrentFileformat().getDigitalDocument();
                DocStruct physical = dd.getPhysicalDocStruct();
                int physPageNumber = physical.getAllChildren() == null ? 1 : physical.getAllChildren().size() + 1;
                DocStruct page = dd.createDocStruct(prefs.getDocStrctTypeByName("page"));

                // phys + log page numbers
                Metadata mdLog = new Metadata(prefs.getMetadataTypeByName("logicalPageNumber"));
                mdLog.setValue("uncounted");
                page.addMetadata(mdLog);

                Metadata mdPhys = new Metadata(prefs.getMetadataTypeByName("physPageNumber"));
                mdPhys.setValue(String.valueOf(physPageNumber));
                page.addMetadata(mdPhys);

                // link to logical docstruct
                DocStruct logical = dd.getLogicalDocStruct();
                logical.addReferenceTo(page, "logical_physical");

                // add to physical sequence
                physical.addChild(page);
            } catch (UGHException e) {
                log.error(e);
            }

        } catch (IOException | SwapException | DAOException e) {
            log.error(e.getMessage(), e);
            Helper.setFehlerMeldung("uploadFailed");
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }

        }

    }

    private String getFileName(final Part part) {
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }

    public boolean showFieldInSearchResult() {
        if (group != null) {
            for (MetadataField val : subfields) {
                if (!"publish".equals(val.getConfigField().getFieldType()) && val.getConfigField().isShowInSearch() && !val.getConfigField().isGroup()
                        && StringUtils.isNotBlank(val.getMetadata().getValue())) {
                    return true;
                }
            }
        } else if (StringUtils.isNotBlank(metadata.getValue())) {
            return true;
        }
        return false;
    }

    @Data
    @RequiredArgsConstructor
    public class SourceField {
        @NonNull
        private String sourceId;
        @NonNull
        private String sourceUri;
        @NonNull
        private String sourceName;
        @NonNull
        private String sourceType;
        @NonNull
        private String sourceLink;
        @NonNull
        private String pageRange;

        private boolean showDetails;

        public boolean isShowDetails() {
            return showDetails;
        }

        public void setShowDetails(boolean showDetails) {
            this.showDetails = showDetails;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            result = prime * result + ((pageRange == null) ? 0 : pageRange.hashCode());
            result = prime * result + (showDetails ? 1231 : 1237);
            result = prime * result + ((sourceId == null) ? 0 : sourceId.hashCode());
            result = prime * result + ((sourceLink == null) ? 0 : sourceLink.hashCode());
            result = prime * result + ((sourceName == null) ? 0 : sourceName.hashCode());
            result = prime * result + ((sourceType == null) ? 0 : sourceType.hashCode());
            result = prime * result + ((sourceUri == null) ? 0 : sourceUri.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            SourceField other = (SourceField) obj;

            return sourceId.equals(other.sourceId);
        }

        private MetadataField getEnclosingInstance() {
            return MetadataField.this;
        }

    }
}
