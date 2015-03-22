import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Consumer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;

/**
 * Created by tzn on 21/03/15.
 */
public class IdemCompletionService implements Runnable {

    private final Module module;

    public IdemCompletionService(Module module) {
        this.module = module;
    }

    @Override
    public void run() {
        loop();
    }

    private <T> T runReadAction(final Computable<T> action) {
        return ApplicationManager.getApplication().runReadAction(action);
    }

    private <T> T runWriteAction(final Computable<T> action) {
        return ApplicationManager.getApplication().runWriteAction(action);
    }

    private <T> T executeCommand(final Computable<T> action) {
        Project project = module.getProject();
        final Object[] o = new Object[1];
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            @Override
            public void run() {
                o[0] = runWriteAction(action);
            }
        }, "COMMAND", null);
        return (T) o[0];
    }

    private void executePooled(final Computable<?> action) {
        ApplicationManager.getApplication().executeOnPooledThread(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return runReadAction(action);
            }
        });
    }

    private void loop() {

        final Project project = module.getProject();
        final VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl("file:///home/tzn/IdeaProjects/untitled/src/Test.java");
        PsiFile originalFile = PsiManager.getInstance(project).findFile(virtualFile);
        final FileEditor selectedEditor = FileEditorManager.getInstance(project).openFile(virtualFile, false)[0];

        Socket socket;
        final PrintWriter out;
        BufferedReader in;
        try {
            ServerSocket serverSocket = new ServerSocket(8080);
            socket = serverSocket.accept();
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        while (socket.isConnected()) {
            String line = null;
            try {
                line = in.readLine();
            } catch (IOException e) {
                return;
            }

            if (line.contains("x")) {
                break;
            }


            final int offset;
            try {
                offset = Integer.parseInt(line);
            } catch (NumberFormatException e2) {
                continue;
            }

            final PsiFile psiFile = (PsiFile) originalFile.copy();
            final Document documentCopy = psiFile.getViewProvider().getDocument();

            /*
            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
                @Override
                public void run() {
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                        @Override
                        public void run() {
                            documentCopy.replaceString(offset, offset, CompletionUtilCore.DUMMY_IDENTIFIER);
                        }
                    });
                }
            }, "WRITE", null);
            */

            // Reload PsiFile.
            /*
            VirtualFile virtualFileCopy = FileDocumentManager.getInstance().getFile(documentCopy);
            psiFile = PsiManager.getInstance(project).findFile(virtualFileCopy);
            */

            final int newOffset = offset;

            out.println("offset: " + newOffset);
            out.println("fragment: " + documentCopy.getText().substring(newOffset - 10, newOffset) + "#");


            executePooled(new Computable<Object>() {
                @Override
                public Object compute() {
                    final PsiElement psiElement = psiFile.findElementAt(newOffset);
                    out.println("element: " + psiElement);
                    OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(project, virtualFile);
                    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

                    final CompletionParameters completionParameters;
                    final Constructor<CompletionParameters> c = (Constructor<CompletionParameters>) CompletionParameters.class.getDeclaredConstructors()[0];
                    c.setAccessible(true);
                    try {
                        completionParameters = c.newInstance(psiElement, psiFile, CompletionType.BASIC, newOffset, 0, editor);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }

                    final StringBuilder stringBuilder = new StringBuilder();
                    Consumer<CompletionResult> consumer = new Consumer<CompletionResult>() {
                        @Override
                        public void consume(CompletionResult completionResult) {
                            stringBuilder.append(completionResult.getLookupElement().toString());
                            stringBuilder.append("\n");
                        }
                    };

                    CompletionService.getCompletionService().performCompletion(completionParameters, consumer);
                    out.write(stringBuilder.toString());
                    out.println();
                    out.flush();
                    return null;
                }
            });
        }
    }
}
