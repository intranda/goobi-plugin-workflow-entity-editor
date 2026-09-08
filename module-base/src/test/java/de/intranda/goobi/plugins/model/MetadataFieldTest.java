package de.intranda.goobi.plugins.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import de.intranda.goobi.plugins.model.MetadataField.SourceField;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataGroupType;
import ugh.dl.MetadataType;

public class MetadataFieldTest {

    private static MetadataField row(ConfiguredField configField, String value) throws Exception {
        MetadataType type = new MetadataType();
        type.setName(configField.getMetadataName());
        Metadata metadata = new Metadata(type);
        metadata.setValue(value);

        MetadataField field = new MetadataField();
        field.setConfigField(configField);
        field.setMetadata(metadata);
        return field;
    }

    /**
     * Every row of a repeatable metadata field is an entry of its own, even when two rows hold the same value. Value based equality makes
     * them interchangeable, which is what lets List.remove() delete a different row than the one the user clicked.
     */
    @Test
    public void testRowsWithTheSameValueAreDistinct() throws Exception {
        ConfiguredField configField = new ConfiguredField("label", "input", "Note");
        MetadataField first = row(configField, "same");
        MetadataField second = row(configField, "same");

        assertNotEquals(first, second);
    }

    /**
     * Deleting the second row of a repeatable field has to keep the first one, otherwise the row the user wanted to delete stays on screen
     * while its metadata is detached from the document.
     */
    @Test
    public void testRemovingARowKeepsTheOtherRowWithTheSameValue() throws Exception {
        ConfiguredField configField = new ConfiguredField("label", "input", "Note");
        MetadataField first = row(configField, "same");
        MetadataField second = row(configField, "same");
        List<MetadataField> rows = new ArrayList<>(Arrays.asList(first, second));

        rows.remove(second);

        assertEquals(1, rows.size());
        assertSame(first, rows.get(0));
    }

    /**
     * SourceField.hashCode() mixes in the hash of its enclosing MetadataField, whose generated hashCode hashes the source list again.
     */
    @Test
    public void testHashCodeOfFieldWithSourceTerminates() throws Exception {
        MetadataField field = row(new ConfiguredField("label", "input", "Note"), "value");
        field.addSource(field.new SourceField("42", null, "name", "Primary", null, null), null);

        field.hashCode();
    }

    /**
     * Sources are read from the METS file, so a source group without a SourceID value yields a SourceField without an id. Deleting such a
     * source must not run into a NullPointerException out of SourceField.equals().
     */
    @Test
    public void testRemoveSourceWithoutSourceId() throws Exception {
        MetadataField field = row(new ConfiguredField("label", "input", "Note"), "value");
        SourceField source = field.new SourceField(null, null, "name", "Primary", null, null);
        field.addSource(source, null);

        field.removeSource(source);

        assertTrue(field.getSources().isEmpty());
    }

    /**
     * The same holds for the other side of that comparison: a source group in the METS file can carry a SourceID without a value, and
     * looking for the group to delete must not stumble over it.
     */
    @Test
    public void testRemoveSourceFromGroupWithoutSourceIdValue() throws Exception {
        MetadataType sourceIdType = new MetadataType();
        sourceIdType.setName("SourceID");

        MetadataGroupType sourceGroupType = new MetadataGroupType();
        sourceGroupType.setName("Source");
        sourceGroupType.addMetadataType(sourceIdType, "*", false, false);
        MetadataGroup sourceGroup = new MetadataGroup(sourceGroupType);
        sourceGroup.addMetadata(new Metadata(sourceIdType));

        MetadataGroupType groupType = new MetadataGroupType();
        groupType.setName("Person");
        MetadataGroup group = new MetadataGroup(groupType);
        // nesting through addMetadataGroup() would need a ruleset that declares Source as an allowed group type
        group.getAllMetadataGroups().add(sourceGroup);

        MetadataField field = new MetadataField();
        field.setConfigField(new ConfiguredField("label", "group", "Person"));
        field.setGroup(group);
        field.setAllowSources(true);
        SourceField source = field.new SourceField("42", null, "name", "Primary", null, null);
        field.addSource(source, null);

        field.removeSource(source);

        assertTrue(field.getSources().isEmpty());
    }
}
