package com.enums.pulgin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * @author: lingjun.jlj
 * @date: 2018/9/25 17:28
 * @description:
 */
public class EnumGenerator extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        //获取当前编辑的文件
        PsiFile originalPsiFile = e.getData(LangDataKeys.PSI_FILE);
        //获取上下文
        DataContext dataContext = e.getDataContext();

        // 获取到数据上下文后，通过CommonDataKeys对象可以获得该File的所有信息
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);

        // 获取当前的project对象
        Project project = e.getProject();

        PsiClass psiClass = getPsiClassFromContext(e, psiFile, editor);
        if (psiClass != null && psiClass.isEnum()) {
            generateEnumMethod(psiClass, originalPsiFile, project, editor);
        }

    }

    /**
     * 获取文本中的
     */
    private PsiClass getPsiClassFromContext(AnActionEvent e, PsiFile psiFile, Editor editor) {

        if (psiFile == null || editor == null) {
            return null;
        }
        //获取插入的model，并获取偏移量
        int offset = editor.getCaretModel().getOffset();
        //根据偏移量找到psi元素
        PsiElement element = psiFile.findElementAt(offset);
        //根据元素获取到当前的上下文的类
        return PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }

    /**
     * 实现 Generate
     */
    private void generateEnumMethod(PsiClass psiClass, PsiFile originalPsiFile, Project project, Editor editor) {
        if (psiClass == null) {
            return;
        }
        //获取对象内具体内容
        PsiField[] psiFields = psiClass.getFields();
        if (psiFields.length > 0) {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                boolean hasNull = false;
                StringBuilder paramBody = new StringBuilder();
                StringBuilder methodBody = new StringBuilder();
                // 通过获取到PsiElementFactory来创建相应的Element，包括字段，方法，注解，类，内部类等等
                PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
                //为每个field创建方法
                PsiClassType psiClassType = PsiType.getTypeByName(psiClass.getName(), project, GlobalSearchScope.fileScope(originalPsiFile));
                for (PsiField psiField : psiFields) {
                    //过滤枚举常量
                    if (StringUtils.contains(psiField.getType().getCanonicalText(), psiClassType.getName())) {
                        if (StringUtils.contains(psiField.getName(), "Null")) {
                            hasNull = true;
                        }
                    }
                    // 生成具体方法
                    else {
                        addValueOfMethodBody(psiField, psiClass, factory);
                        addGetMethod(psiField, psiClass, factory);
                        addSetMethod(psiField, psiClass, factory);
                        createConstructor(psiField, paramBody, methodBody);
                    }
                }

                //生成默认枚举常量
                if (!hasNull) {
                    PsiEnumConstant psiEnumConstant = factory.createEnumConstantFromText("null", psiClass);
                    psiClass.add(psiEnumConstant);
                }

                PsiMethod[] cons = psiClass.getConstructors();

                for (PsiMethod con : cons) {
                    if (con.getParameters().length == 0) {
                        con.delete();
                    }
                }
                //无参构造
//                PsiMethod noParamConstructor = factory.createConstructor();
//                psiClass.add(noParamConstructor);
                //获取类名
                String className = psiClass.getNameIdentifier().getText();
                createConstructor(className, psiClass, factory, paramBody, methodBody);
            });
        }

    }

    /**
     * 添加valueOf方法
     *
     * @param psiField
     * @param psiClass
     * @param factory
     */
    private void addValueOfMethodBody(PsiField psiField, PsiClass psiClass, PsiElementFactory factory) {
        //参数名
        String paramName = psiField.getName();
        //参数类型
        String paramType = psiField.getType().getCanonicalText();
        //返回类型
        String returnType = psiClass.getName();
        //获取方法名称,首字母大写
        String methodName = "valueOf" + StringUtils.capitalize(paramName);

        for (PsiMethod psiMethod : psiClass.findMethodsByName(methodName, true)) {
            psiMethod.delete();
        }

        String body = "public static " + returnType + " " + methodName + "(" + paramType + " " + paramName + ") {\n" +
                "        for (" + returnType + " obj : " + returnType + ".values()) {\n" +
                "            if (java.util.Objects.equals(obj." + paramName + "," + paramName + ") " + ") {\n" +
                "                return obj;\n" +
                "            }\n" +
                "        }\n" +
                "        return Null;\n" +
                "    }";
        PsiMethod psiMethod = factory.createMethodFromText(body, psiClass);
        psiClass.add(psiMethod);
    }

    /**
     * 添加get方法
     *
     * @param psiField
     * @param psiClass
     * @param factory
     */
    private void addGetMethod(PsiField psiField, PsiClass psiClass, PsiElementFactory factory) {
        String paramName = psiField.getName();
        String paramType = psiField.getType().getCanonicalText();
        boolean isBool = StringUtils.containsIgnoreCase(paramType, "boolean");

        String methodName = (isBool ? "is" : "get") + StringUtils.capitalize(paramName);
        for (PsiMethod psiMethod : psiClass.findMethodsByName(methodName, true)) {
            psiMethod.delete();
        }

        String body = "public " + paramType + " " + methodName + "() {\n" +
                "        return " + paramName + ";\n" +
                "    }";


        PsiMethod psiMethod = factory.createMethodFromText(body, psiClass);
        psiClass.add(psiMethod);
    }

    /**
     * 添加set方法
     *
     * @param psiField
     * @param psiClass
     * @param factory
     */
    private void addSetMethod(PsiField psiField, PsiClass psiClass, PsiElementFactory factory) {
        //参数名
        String paramName = psiField.getName();
        //参数类型
        String paramType = psiField.getType().getCanonicalText();
        boolean isBool = StringUtils.containsIgnoreCase(paramType, "boolean");
        //获取方法名称
        String methodName = (isBool ? "is" : "set") + StringUtils.capitalize(paramName);

        for (PsiMethod psiMethod : psiClass.findMethodsByName(methodName, true)) {
            psiMethod.delete();
        }


        String body = "public void " + methodName + "(" + paramType + " " + paramName + ") {\n" +
                "        this. " + paramName + " = " + paramName + ";\n" +
                "    }";
        PsiMethod psiMethod = factory.createMethodFromText(body, psiClass);
        psiClass.add(psiMethod);
    }

    /**
     * 拼接全参构造函数参数
     *
     * @param psiField
     * @param paramBody
     * @param methodBody
     */
    public void createConstructor(PsiField psiField, StringBuilder paramBody, StringBuilder methodBody) {
        //参数名
        String paramName = psiField.getName();
        //参数类型
        String paramType = psiField.getType().getCanonicalText();
        //参数拼接
        if (StringUtils.isNotBlank(paramBody)) {
            paramBody.append(", ");
        }
        paramBody.append(paramType)
                .append(" ")
                .append(paramName);
        //具体方法拼接
        methodBody.append("        this. ")
                .append(paramName)
                .append(" = ").append(paramName).append(";\n");

    }

    /**
     * 生成全参数构造函数
     *
     * @param className  类名
     * @param psiClass
     * @param factory
     * @param paramBody
     * @param methodBody
     */
    public void createConstructor(String className,
                                  PsiClass psiClass,
                                  PsiElementFactory factory,
                                  StringBuilder paramBody,
                                  StringBuilder methodBody) {

        String body = className + "(" + paramBody.toString() + ") {\n" +
                methodBody.toString() +
                "    }";

        PsiMethod psiMethod = factory.createMethodFromText(body, psiClass);
        psiClass.add(psiMethod);

    }

}
