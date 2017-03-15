package com.dailymotion.aa2bk;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;

import java.lang.reflect.Executable;
import java.util.*;

public class AA2BK extends AnAction {

    private Project mProject;
    private Set<String> mProcessedNames = new HashSet<>();
    private Set<String> mProcessedQualifedNames = new HashSet<>();

    private void processClass(PsiClass psiClass) {
        System.out.println("processClass " + psiClass);

        PsiAnnotation psiAnnotation = Util.getAnnotation(psiClass, "EViewGroup");
        String layoutId = Util.getAnnotationParameter(psiAnnotation);
        if (layoutId == null) {
            System.err.println("cannot find layoutId for " + psiClass);
            return;
        }
        psiAnnotation.delete();

        String body = String.format("" +
                        "public static %s build(Context context) {" +
                        "   return (%s) LayoutInflater.from(context).inflate(%s, null);" +
                        "}",
                psiClass.getName(),
                psiClass.getName(),
                layoutId);

        PsiMethod method = JavaPsiFacade.getElementFactory(mProject).createMethodFromText(body, psiClass);
        psiClass.add(method);

        Util.addImportIfNeeded(mProject, psiClass.getContainingFile(), "android.view.LayoutInflater");

        for (PsiElement child : psiClass.getChildren()) {
            if (child instanceof PsiField) {
                processField((PsiField) child);
            } else if (child instanceof PsiMethod) {
                processMethod((PsiMethod)child);
            }
        }

        PsiMethod onFinishInflatePsiMethod = null;
        PsiMethod afterViewsPsiMethod = null;
        for (PsiElement child: psiClass.getChildren()) {
            if (child instanceof PsiMethod) {
                if (((PsiMethod) child).getName().equals("onFinishInflate")) {
                    onFinishInflatePsiMethod = (PsiMethod) child;
                    break;
                } else {
                    PsiAnnotation psiAnnotation2 = Util.getAnnotation(child, "AfterViews");
                    if (psiAnnotation2 != null) {
                        afterViewsPsiMethod = (PsiMethod) child;
                    }
                }
            }
        }

        if (onFinishInflatePsiMethod == null) {
            body = "@Override" +
                    " protected void onFinishInflate() {" +
                    "super.onFinishInflate();" +
                    "}";

            onFinishInflatePsiMethod = JavaPsiFacade.getElementFactory(mProject).createMethodFromText(body, psiClass);
            onFinishInflatePsiMethod = (PsiMethod) psiClass.add(onFinishInflatePsiMethod);
        }

        PsiCodeBlock codeBlock = null;
        for (PsiElement element: onFinishInflatePsiMethod.getChildren()) {
            if (element instanceof PsiCodeBlock) {
                codeBlock = (PsiCodeBlock) element;
                break;
            }
        }
        codeBlock.addAfter(JavaPsiFacade.getElementFactory(mProject).createStatementFromText("ButterKnife.bind(this);", psiClass), codeBlock.getLastBodyElement());
        try {
            Util.addImportIfNeeded(mProject, psiClass.getContainingFile(), "butterknife.ButterKnife");
            Util.addImportIfNeeded(mProject, psiClass.getContainingFile(), "butterknife.BindView");
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (afterViewsPsiMethod != null) {
            PsiCodeBlock codeBlock1 = null;
            for (PsiElement element: afterViewsPsiMethod.getChildren()) {
                if (element instanceof PsiCodeBlock) {
                    codeBlock1 = (PsiCodeBlock) element;
                    break;
                }
            }
            for (int i = 1; i < codeBlock1.getChildren().length - 1; i++) {
                codeBlock.addAfter(codeBlock1.getChildren()[i], codeBlock.getLastBodyElement());
            }

            afterViewsPsiMethod.delete();
        }

        mProcessedNames.add(psiClass.getName());
        mProcessedQualifedNames.add(psiClass.getQualifiedName());
    }

    private void processMethod(PsiMethod psiMethod) {
        List<PsiAnnotation> annotationList = Util.findElements(psiMethod, PsiAnnotation.class);

        for (PsiAnnotation annotation : annotationList) {
            String annotationName = Util.getAnnotationName(annotation);
            String resourceName = Util.getAnnotationParameter(annotation);

            if (annotationName.equals("Click")) {
                if (resourceName != null) {
                    String a = String.format("@OnClick(%s)", resourceName);
                    PsiAnnotation newAnnotation = JavaPsiFacade.getInstance(mProject).getElementFactory().createAnnotationFromText(a, psiMethod);
                    annotation.replace(newAnnotation);

                    Util.addImportIfNeeded(mProject, psiMethod.getContainingFile(), "butterknife.OnClick");
                    return;
                }
            }
        }
    }

    private void processField(PsiField psiField) {
        List<PsiAnnotation> annotationList = Util.findElements(psiField, PsiAnnotation.class);

        for (PsiAnnotation annotation : annotationList) {
            String annotationName = Util.getAnnotationName(annotation);
            String resourceName = Util.getAnnotationParameter(annotation);

            if (annotationName.equals("ViewById")) {

                for (PsiElement child : psiField.getChildren()) {
                    if (child instanceof PsiIdentifier) {
                        if (resourceName == null) {
                            resourceName = "R.id." + child.getText();
                        } else {
                            System.out.println("explicit id: " + resourceName);
                        }
                        String a = String.format("@BindView(%s)", resourceName);
                        PsiAnnotation newAnnotation = JavaPsiFacade.getInstance(mProject).getElementFactory().createAnnotationFromText(a, psiField);
                        annotation.replace(newAnnotation);
                        return;
                    }
                }
            }
        }
    }

    public void actionPerformed(AnActionEvent event) {
        mProject = event.getData(PlatformDataKeys.PROJECT);
        WriteCommandAction.runWriteCommandAction(mProject, () -> {
            Util.traverseFiles(mProject, new HashSet<>(Arrays.asList(".java")), psiFile -> {
                for (PsiElement psiElement: psiFile.getChildren()) {
                    if (psiElement instanceof PsiClass) {
                        if (Util.getAnnotation(psiElement, "EViewGroup") != null) {
                            processClass((PsiClass) psiElement);
                        }
                    }
                }
            });
            Util.traverseFiles(mProject, new HashSet<>(Arrays.asList(".java", ".xml")), psiFile -> {
                processIdentifiers(psiFile);
            });
        });
    }

    private void processIdentifiers(PsiFile psiFile) {
        psiFile.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                super.visitElement(element);

                if (element instanceof PsiIdentifier) {
                    String identifier = element.getText();
                    if (identifier.charAt(identifier.length() - 1) == '_') {
                        String newIdentifier = identifier.substring(0, identifier.length() - 1);
                        if (mProcessedNames.contains(newIdentifier)) {
                            System.out.println("rename " + identifier + " -> " + newIdentifier);
                            element.replace(JavaPsiFacade.getInstance(mProject).getElementFactory().createIdentifier(newIdentifier));
                        }
                    }
                } else if (element instanceof XmlToken && ((XmlToken) element).getTokenType().equals(XmlTokenType.XML_NAME)) {
                    String identifier = element.getText();
                    if (identifier.charAt(identifier.length() - 1) == '_') {
                        String newIdentifier = identifier.substring(0, identifier.length() - 1);
                        if (mProcessedQualifedNames.contains(newIdentifier)) {
                            final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
                            manipulator.handleContentChange(element, new TextRange(0, identifier.length()), newIdentifier);
                        }
                    }
                }
            }
        });
    }
}