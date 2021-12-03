package de.intranda.goobi.plugins.model;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.faces.validator.ValidatorException;
import javax.servlet.http.Part;

import org.apache.commons.lang.StringUtils;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;

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

    @Getter
    private List<SourceField> sources = new ArrayList<>();

    public void addSource(SourceField field) {
        sources.add(field);
    }

    public void removeSource(SourceField field) {
        sources.remove(field);
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
                if ("publish".equals( mf.getConfigField().getFieldType())) {
                    publishField = mf;
                    return true;
                }
            }
        }
        return false;
    }



    public void dateValidator(FacesContext context, UIComponent component, Object value) {
        valid = true;
        validationErrorMessage=null;
        if (value == null) {
            if (configField.isRequired()) {
                valid=false;
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "validation error", "TODO Field is required");
                validationErrorMessage="Field is required";
                throw new ValidatorException(message);
            }
        } else {
            String dateValue = (String) value;
            if (!dateValue.matches("\\d\\d\\d\\d") && !dateValue.matches("\\d\\d\\d\\d-\\d\\d-\\d\\d") ) {
                valid=false;
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "validation error", "TODO invalid format");
                validationErrorMessage="invalid format";
                throw new ValidatorException(message);
            }

        }

    }



    @Getter @Setter
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

            String filename = ConfigurationHelper.getInstance().getTemporaryFolder() + basename;

            inputStream = this.uploadedFile.getInputStream();
            outputStream = new FileOutputStream(filename);

            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
            // TODO copy into process folder
            metadata.setValue(filename);
        } catch (IOException e) {
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
    }
}
