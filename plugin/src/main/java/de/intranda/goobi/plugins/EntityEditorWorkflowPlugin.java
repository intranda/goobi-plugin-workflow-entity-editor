package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.goobi.beans.Process;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;

import de.intranda.goobi.plugins.model.BreadcrumbItem;
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

    @Getter
    private String entityType;

    @Getter
    private String entityColor;
    @Getter
    private String entityIcon;
    @Getter
    private List<BreadcrumbItem> breadcrumbList = new ArrayList<>();

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

            BreadcrumbItem root = new BreadcrumbItem("dashboard", "Dashboard", 0, "#ccc", null);
            breadcrumbList.add(root);

            BreadcrumbItem item = new BreadcrumbItem("person", "John Doe", currentProcess.getId(), "#df07b9", "fa-user");
            breadcrumbList.add(item);

            BreadcrumbItem item2 = new BreadcrumbItem("award", "Darwin Award", currentProcess.getId(), "#05b8cd", "fa-trophy");
            breadcrumbList.add(item2);

            BreadcrumbItem item3 = new BreadcrumbItem("work", "Mona Lisa", currentProcess.getId(), "#900688", "fa-picture-o");
            breadcrumbList.add(item3);

            BreadcrumbItem item4 = new BreadcrumbItem("event", "World Cup", currentProcess.getId(), "#19b609", "fa-calendar");
            breadcrumbList.add(item4);

            BreadcrumbItem item5 = new BreadcrumbItem("agency", "intranda", currentProcess.getId(), "#e81c0c", "fa-university");
            breadcrumbList.add(item5);

            entityType = "person";
            entityColor = "#df07b9";
            entityIcon = "fa-user";

        } catch (UGHException | IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }
    }

}
