package com.dailymotion.aa2bk;

import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by martin on 2/24/17.
 */
public class Util {
    public static void addImportIfNeeded(Project project, PsiFile psiFile, String qualifiedName) {
        PsiClass layoutInflaterPsiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
        PsiImportList psiImportList = findElement(psiFile, PsiImportList.class);
        for (PsiElement child: psiImportList.getChildren()) {
            PsiJavaCodeReferenceElement e = findElement(child, PsiJavaCodeReferenceElement.class);
            if (e != null && e.getText().equals(qualifiedName)) {
                // we already have the reference, to not add it
                return;
            }
        }

        psiImportList.add(JavaPsiFacade.getElementFactory(project).createImportStatement(layoutInflaterPsiClass));
    }

    public static String getAnnotationName(PsiAnnotation psiAnnotation) {
        for (PsiElement child : psiAnnotation.getChildren()) {
            if (child instanceof PsiJavaCodeReferenceElement) {
                for (PsiElement child2 : child.getChildren()) {
                    if (child2 instanceof PsiIdentifier) {
                        return child2.getText();
                    }
                }
            }
        }
        return "?";
    }
    public static PsiAnnotation getAnnotation(PsiElement element, String name) {
        for (PsiElement child : element.getChildren()) {
            if (child instanceof PsiModifierList) {
                for (PsiElement child2 : child.getChildren()) {
                    if (child2 instanceof PsiAnnotation) {
                        if (name.equals(getAnnotationName((PsiAnnotation) child2))) {
                            return (PsiAnnotation) child2;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static String getAnnotationParameter(PsiAnnotation annotation) {
        PsiElement psiNameValuePair = Util.findElement(annotation, PsiNameValuePair.class);
        if (psiNameValuePair == null) {
            return null;
        }

        return psiNameValuePair.getText();
    }
    public  static <T extends PsiElement> T findElement(PsiElement psiElement, Class<T> clazz) {

        List<T> list = findElements(psiElement, clazz);
        if (list.size() > 0) {
            return list.get(0);
        } else {
            return null;
        }
    }

    public  static <T extends PsiElement> List<T> findElements(PsiElement psiElement, Class<T> clazz) {
        final List<T> list = new ArrayList<T>();
        psiElement.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                super.visitElement(element);
                if (clazz.isInstance(element)) {
                    list.add((T) element);
                }
            }
        });

        return list;
    }


    interface JavaFileIterator {
        void processFile(PsiFile psiFile);
    }

    public static void traverseFiles(Project project, Set<String> extensions, JavaFileIterator iterator) {
        ProjectFileIndex.SERVICE.getInstance(project).iterateContent(fileOrDir -> {
            String n = fileOrDir.toString();
            int i = n.lastIndexOf('.');
            if (i < 0) {
                return true;
            }
            String ext = n.substring(i);
            if (extensions.contains(ext)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(fileOrDir);
                if (psiFile != null) {
                    iterator.processFile(psiFile);
                } else {
                    System.err.println("could not find psiFile for: " + n);
                }
            }
            return true;
        });
    }
}
