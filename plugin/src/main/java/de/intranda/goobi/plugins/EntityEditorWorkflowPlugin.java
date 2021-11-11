package de.intranda.goobi.plugins;

import java.io.IOException;

import org.goobi.beans.Process;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;

import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j2
public class EntityEditorWorkflowPlugin implements IWorkflowPlugin, IPlugin {

    @Getter
    private String title = "intranda_workflow_entity_editor";

    @Getter
    private String value;

    @Getter
    private PluginType type = PluginType.Workflow;

    @Getter
    private String gui = "/uii/plugin_workflow_entity_editor.xhtml";

    @Getter
    private Process currentProcess;
    @Getter
    private Fileformat currentFileformat;
    @Getter
    private Prefs prefs;

    /**
     * Constructor
     */
    public EntityEditorWorkflowPlugin() {
        loadTestdata();
    }

    public void loadTestdata() {

        // load testdata
        try {
            currentProcess = ProcessManager.getProcessByExactTitle("SamplePerson");
            prefs = currentProcess.getRegelsatz().getPreferences();

            currentFileformat = new MetsMods(prefs);
            currentFileformat.read(currentProcess.getMetadataFilePath());

        } catch (UGHException | IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }
    }

}
