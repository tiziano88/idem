import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by tzn on 21/03/15.
 */
public class Mod implements ModuleComponent {

    private final Module module;

    public Mod(Module module) {
        this.module = module;
    }

    private ExecutorService getExecutorService() {
        return Executors.newFixedThreadPool(10);
    }

    public void initComponent() {
        IdemCompletionService completionService = new IdemCompletionService(module);
        ApplicationManager.getApplication().executeOnPooledThread(completionService);
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "Comp";
    }

    public void projectOpened() {
        // called when project is opened
    }

    public void projectClosed() {
        // called when project is being closed
    }

    public void moduleAdded() {
        // Invoked when the module corresponding to this component instance has been completely
        // loaded and added to the project.
    }
}
