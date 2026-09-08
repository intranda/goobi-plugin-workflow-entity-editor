package de.intranda.goobi.plugins.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.powermock.reflect.Whitebox;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;

public class EntityTest {

    /**
     * The Entity constructor opens the process and reads its METS file. The metadata actions under test only work on the field they are
     * called with, so the parts of an entity they do use are assigned directly.
     */
    private static Entity entity() {
        Entity entity = Whitebox.newInstance(Entity.class);
        Whitebox.setInternalState(entity, "prefs", new Prefs());
        return entity;
    }

    private static MetadataType metadataType(String name) {
        MetadataType type = new MetadataType();
        type.setName(name);
        return type;
    }

    private static DocStruct logicalDocStruct(MetadataType allowedType) throws Exception {
        DocStructType docStructType = new DocStructType();
        docStructType.setName("Person");
        docStructType.addMetadataType(allowedType, "*");

        DigitalDocument document = new DigitalDocument();
        DocStruct logical = document.createDocStruct(docStructType);
        document.setLogicalDocStruct(logical);
        return logical;
    }

    private static MetadataField row(ConfiguredField configField, DocStruct parent, MetadataType type, String value) throws Exception {
        Metadata metadata = new Metadata(type);
        metadata.setValue(value);
        if (parent != null) {
            parent.addMetadata(metadata);
        }

        MetadataField field = new MetadataField();
        field.setConfigField(configField);
        field.setMetadata(metadata);
        configField.adMetadataField(field);
        return field;
    }

    /**
     * Deleting a row of a repeatable field has to delete that row. When two rows hold the same value, the first one used to be dropped
     * instead, leaving the clicked row on screen with its metadata detached from the document - and a second click on it then failed.
     */
    @Test
    public void testRemoveMetadataRemovesTheSelectedRow() throws Exception {
        MetadataType type = metadataType("Note");
        DocStruct logical = logicalDocStruct(type);
        ConfiguredField configField = new ConfiguredField("label", "input", "Note");
        configField.setRepeatable(true);
        MetadataField first = row(configField, logical, type, "same");
        MetadataField second = row(configField, logical, type, "same");

        entity().removeMetadata(second);

        assertEquals(1, configField.getMetadataList().size());
        assertSame(first, configField.getMetadataList().get(0));
    }

    /**
     * A metadata that is no longer attached to the document cannot be removed from it a second time. The row still has to disappear from
     * the field instead of throwing out of the JSF invoke application phase, so that an editor which already got into that state can be
     * cleaned up by the user.
     */
    @Test
    public void testRemoveMetadataOfDetachedMetadata() throws Exception {
        MetadataType type = metadataType("Note");
        ConfiguredField configField = new ConfiguredField("label", "input", "Note");
        configField.setRepeatable(true);
        MetadataField detached = row(configField, null, type, "value");

        entity().removeMetadata(detached);

        assertTrue(configField.getMetadataList().isEmpty());
    }

    /**
     * Adding a metadata fails when the ruleset does not allow another occurrence of it for the doc struct. The field must not be flipped to
     * shown then, because there is no row to show.
     */
    @Test
    public void testDuplicateMetadataKeepsFieldClosedWhenAddingFails() {
        ConfiguredField configField = new ConfiguredField("label", "input", "Note");

        entity().duplicateMetadata(configField);

        assertTrue(configField.getMetadataList().isEmpty());
        assertFalse(configField.isShowField());
    }
}
