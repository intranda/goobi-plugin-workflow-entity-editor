package de.intranda.goobi.plugins.model;

import java.awt.image.RenderedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.metadaten.Image;
import de.sub.goobi.validator.EDTFValidator;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentLibException;
import de.unigoettingen.sub.commons.contentlib.imagelib.ImageInterpreter;
import de.unigoettingen.sub.commons.contentlib.imagelib.ImageManager;
import de.unigoettingen.sub.commons.contentlib.imagelib.JpegInterpreter;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.Part;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
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

    private Image image = null;

    @Getter
    @Setter
    private Part uploadedFile = null;

    public MetadataField() {
    }

    public MetadataField(MetadataField mf) {
        // do nothing
    }

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
                if ("Source".equals(mg.getType().getName())) {
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
            for (VocabularyEntry item : configField.getVocabularyList()) {
                if (value.equals(item.getMainValue())) {
                    metadata.setValue(item.getMainValue());
                    metadata.setAuthorityFile(configField.getVocabularyName(), configField.getVocabularyUrl(),
                            item.getEntryUrl());
                }
            }
        }
    }

    public String getVocabularyValue() {
        String label = metadata.getValue();
        if (StringUtils.isNotBlank(label)) {
            for (VocabularyEntry item : configField.getVocabularyList()) {
                if (label.equals(item.getMainValue())) {
                    return item.getMainValue();
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

    public void dateValidator(FacesContext context, UIComponent component, Object value) { //NOSONAR
        valid = true;
        validationErrorMessage = null;
        if (configField.isRequired() && isEmpty(value)) {
            valid = false;
            validationErrorMessage = "Field is required";
        } else {
            String dateValue = (String) value;
            EDTFValidator validator = new EDTFValidator();
            if (!validator.isValid(dateValue)) {
                valid = false;
                validationErrorMessage = "Invalid date format. Dates must comply with EDTF specifications.";
            }
        }
    }

    public void requiredValidator(FacesContext context, UIComponent component, Object value) { //NOSONAR
        valid = true;
        validationErrorMessage = null;
        if (configField.isRequired() && isEmpty(value)) {
            valid = false;
            validationErrorMessage = "Field is required";
        }
    }

    private boolean isEmpty(Object value) {
        String fieldContents = (String) value;
        return (value == null || StringUtils.isBlank(fieldContents));
    }

    /**
     * File upload with binary copying.
     */
    public void uploadFile() {

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

        String uploadFolderName = configField.getEntity().getConfiguration().getUploadFolderName();
        String conversionFolderName = configField.getEntity().getConfiguration().getConversionFolderName();
        boolean updatedFile = false;
        Path file = null;
        try {
            Path uploadDirectory = Paths.get(configField.getEntity().getCurrentProcess().getConfiguredImageFolder(uploadFolderName));
            if (!Files.exists(uploadDirectory)) {
                Files.createDirectory(uploadDirectory);
            }
            file = uploadDirectory.resolve(basename).toAbsolutePath();

            if (StorageProvider.getInstance().isFileExists(file)) {
                updatedFile = true;
            }

            try (InputStream inputStream = this.uploadedFile.getInputStream();
                    OutputStream outputStream = new FileOutputStream(file.toString())) {

                byte[] buf = new byte[1024];
                int len;
                while ((len = inputStream.read(buf)) > 0) {
                    outputStream.write(buf, 0, len);
                }
            }
        } catch (IOException | SwapException | DAOException e1) {
            log.error(e1);
        }
        if (!updatedFile) {
            try {
                Prefs prefs = configField.getEntity().getPrefs();
                DigitalDocument dd = configField.getEntity().getCurrentFileformat().getDigitalDocument();
                addFileToDocument(file, dd, prefs);
            } catch (UGHException e) {
                log.error(e);
            }
        }

        // optional: convert uploaded file to jpeg
        if (StringUtils.isNotBlank(conversionFolderName)) {

            // TODO only convert images, copy other file types instead

            try {
                Path destinationDirectory = Paths.get(configField.getEntity().getCurrentProcess().getConfiguredImageFolder(conversionFolderName));
                if (!Files.exists(destinationDirectory)) {
                    Files.createDirectory(destinationDirectory);
                }

                Path convertedFile = Paths.get(destinationDirectory.toString(),
                        file.getFileName().toString().substring(0, file.getFileName().toString().lastIndexOf(".")) + ".jpg");

                try (ImageManager im = new ImageManager(file.toUri());
                        ImageInterpreter ii = im.getMyInterpreter()) {
                    RenderedImage ri2 = ii.getRenderedImage();
                    try (JpegInterpreter pi = new JpegInterpreter(ri2)) {
                        pi.setXResolution(ii.getXResolution());
                        pi.setYResolution(ii.getYResolution());

                        try (OutputStream outputFileStream = StorageProvider.getInstance().newOutputStream(convertedFile)) {
                            pi.writeToStream(null, outputFileStream);
                        }
                    }
                }
                file = convertedFile;
            } catch (ContentLibException | IOException | SwapException | DAOException e) {
                log.error(e);
            }
        }

        metadata.setValue(file.toString());

        createPage(file);

    }

    private void addFileToDocument(Path file, DigitalDocument dd, Prefs prefs)
            throws TypeNotAllowedForParentException, MetadataTypeNotAllowedException, TypeNotAllowedAsChildException {
        DocStruct physical = dd.getPhysicalDocStruct();
        int physPageNumber = physical.getAllChildren() == null ? 1 : physical.getAllChildren().size() + 1;
        DocStruct page = dd.createDocStruct(prefs.getDocStrctTypeByName("page"));

        ContentFile cf = new ContentFile();
        cf.setMimetype(NIOFileUtils.getMimeTypeFromFile(file));
        cf.setLocation(file.toString());
        page.addContentFile(cf);

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
    }

    private void createPage(Path file) {
        try {
            Prefs prefs = configField.getEntity().getPrefs();
            DigitalDocument dd = configField.getEntity().getCurrentFileformat().getDigitalDocument();
            DocStruct physical = dd.getPhysicalDocStruct();
            int physPageNumber = physical.getAllChildren() == null ? 1 : physical.getAllChildren().size() + 1;
            DocStruct page = dd.createDocStruct(prefs.getDocStrctTypeByName("page"));

            ContentFile cf = new ContentFile();
            cf.setMimetype(NIOFileUtils.getMimeTypeFromFile(file));
            cf.setLocation(file.toString());
            page.addContentFile(cf);

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
    }

    public Image getImage() {
        if (image == null && "fileupload".equals(configField.getFieldType()) && StringUtils.isNotBlank(metadata.getValue())) {
            Path file = Paths.get(metadata.getValue());
            try {

                String foldername = null;
                if (StringUtils.isNotBlank(configField.getEntity().getConfiguration().getConversionFolderName())) {
                    foldername = configField.getEntity()
                            .getCurrentProcess()
                            .getConfiguredImageFolder(configField.getEntity().getConfiguration().getConversionFolderName());
                } else {
                    foldername = configField.getEntity()
                            .getCurrentProcess()
                            .getConfiguredImageFolder(configField.getEntity().getConfiguration().getUploadFolderName());
                }

                image = new Image(configField.getEntity().getCurrentProcess(), foldername, file.getFileName().toString(), 1, 200);
            } catch (IOException | SwapException | DAOException e) {
                log.error(e);
            }
        }
        return image;
    }

    private String getFileName(final Part part) {
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return "";
    }

    public boolean isShowFieldInSearchResult() {
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
    public class SourceField {

        private String sourceId;

        private String sourceUri;

        private String sourceName;

        private String sourceType;

        private String sourceLink;

        private String pageRange;

        private boolean showDetails;

        public SourceField(String sourceId, String sourceUri, String sourceName, String sourceType, String sourceLink, String sourcePageRange) {
            this.sourceId = sourceId;
            this.sourceUri = sourceUri;
            this.sourceName = sourceName;
            this.sourceType = sourceType;
            this.sourceLink = sourceLink;
            this.pageRange = sourcePageRange;

        }

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
            if (other == null) {
                return false;
            }

            return sourceId.equals(other.sourceId);
        }

        private MetadataField getEnclosingInstance() {
            return MetadataField.this;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

    }
}
