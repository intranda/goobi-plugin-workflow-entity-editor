package de.intranda.goobi.plugins;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.BeforeClass;
import org.junit.Test;

import de.sub.goobi.config.ConfigurationHelper;

public class EntityEditorPluginTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        String resourcesFolder = "src/test/resources/"; // for junit tests in eclipse
        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }
        Path goobiFolder = Paths.get(resourcesFolder, "goobi_config.properties");
        ConfigurationHelper.configFileName = goobiFolder.toString();
        ConfigurationHelper.resetConfigurationFile();
        ConfigurationHelper.getInstance().setParameter("goobiFolder", goobiFolder.getParent().toString() + "/");
    }

    @Test
    public void testVersion() throws IOException {
        String s = "xyz";
        assertNotNull(s);
    }

    /**
     * The search field is assigned by the view when a search modal is opened from a metadata field. The search and import actions of those
     * modals stay reachable from a view that never made that assignment, for instance after the plugin bean was recreated while the browser
     * still shows the old page. They have to abort instead of running into a NullPointerException then.
     */
    private EntityEditorWorkflowPlugin pluginWithoutSearchField() {
        EntityEditorWorkflowPlugin plugin = new EntityEditorWorkflowPlugin();
        assertNull(plugin.getSearchField());
        return plugin;
    }

    @Test
    public void testSearchVocabularyWithoutSearchField() {
        EntityEditorWorkflowPlugin plugin = pluginWithoutSearchField();
        plugin.setSearchValue("fixture");
        plugin.searchVocabulary();
    }

    @Test
    public void testImportVocabularyDataWithoutSearchField() {
        pluginWithoutSearchField().importVocabularyData();
    }

    @Test
    public void testSearchGeonamesWithoutSearchField() {
        EntityEditorWorkflowPlugin plugin = pluginWithoutSearchField();
        plugin.setGeonamesSearchValue("fixture");
        plugin.searchGeonames();
    }

    @Test
    public void testImportGeonamesDataWithoutSearchField() {
        pluginWithoutSearchField().importGeonamesData();
    }
}
