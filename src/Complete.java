import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Consumer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by tzn on 20/03/15.
 */
public class Complete extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            return;
        }
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return;
        }
        final Document document = editor.getDocument();
        if (document == null) {
            return;
        }
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        if (virtualFile == null) {
            return;
        }

        PsiFile originalFile = PsiManager.getInstance(project).findFile(virtualFile);

        try {
            ServerSocket serverSocket = new ServerSocket(8080);
            Socket socket = serverSocket.accept();
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (socket.isConnected()) {
                String line = in.readLine();
                if (line.contains("x")) {
                    break;
                }


                final int offset;
                try {
                    offset = Integer.parseInt(line);
                } catch (NumberFormatException e2) {
                    continue;
                }

                PsiFile psiFile = (PsiFile) originalFile.copy();

                CommandProcessor.getInstance().executeCommand(project, new Runnable() {
                    @Override
                    public void run() {
                        ApplicationManager.getApplication().runWriteAction(new Runnable() {
                            @Override
                            public void run() {
                                document.replaceString(offset, offset, CompletionUtilCore.DUMMY_IDENTIFIER);
                            }
                        });
                    }
                }, "WRITE", null);

                int newOffset = offset + 1;

                out.println("offset: " + newOffset);
                out.println("fragment: " + document.getText().substring(newOffset - 10, newOffset) + "#");

                PsiElement psiElement = psiFile.findElementAt(newOffset);
                out.println("element: " + psiElement);


                Constructor<CompletionParameters> c = (Constructor<CompletionParameters>) CompletionParameters.class.getDeclaredConstructors()[0];
                c.setAccessible(true);
                CompletionParameters completionParameters = c.newInstance(psiElement, psiFile, CompletionType.BASIC, newOffset, 0, editor);

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
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            return;
        }


        /*
//        editor.getCaretModel().getCurrentCaret().setSelection(0, 10);
        try {
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        final String contents;
        CodeCompletionHandlerBase handler = new CodeCompletionHandlerBase(CompletionType.BASIC);
        handler.invokeCompletion(project, editor);
//        CompletionParameters completionParameters = new CompletionParameters();
//        contents = com.intellij.codeInsight.completion.CompletionService.getCompletionService().performCompletion();
        final Runnable readRunner = new Runnable() {
            @Override
            public void run() {
//                document.setText("");
            }
        };
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                CommandProcessor.getInstance().executeCommand(project, new Runnable() {
                    @Override
                    public void run() {
                        ApplicationManager.getApplication().runWriteAction(readRunner);
                    }
                }, "READ", null);
            }
        });
        */
    }
}
