package com.rickytech.bpmn.viewer

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile

/**
 * IntelliJ "New" 菜单中的 "BPMN Process" 入口。
 *
 * 通过 File → New 或项目树右键 → New 触发，让用户输入文件名后在当前选中目录下
 * 创建一个最小有效的 .bpmn 文件，并自动打开。新建的空流程会由
 * [BpmnFileEditor] 检测到并自动切换到编辑模式。
 */
class NewBpmnFileAction : AnAction(
    "BPMN Process",
    "Create a new BPMN process file",
    IconLoader.getIcon("/icons/bpmn.svg", NewBpmnFileAction::class.java)
) {

    companion object {
        private val LOG = Logger.getInstance(NewBpmnFileAction::class.java)

        private fun bpmnTemplate(processId: String, processName: String): String {
            return """<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:activiti="http://activiti.org/bpmn"
             targetNamespace="http://www.activiti.org/processdef">
  <process id="$processId" name="$processName" isExecutable="true">
  </process>
</definitions>
"""
        }

        /** 将文件名转换为合法的 BPMN process id：仅保留字母数字下划线，首字符不为数字。 */
        internal fun toProcessId(name: String): String {
            val sanitized = name.map { c ->
                if (c.isLetterOrDigit() || c == '_') c else '_'
            }.joinToString("")
            val safe = if (sanitized.isEmpty()) "process" else sanitized
            return if (safe.first().isDigit()) "p_$safe" else safe
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val dir = resolveTargetDirectory(e)
        e.presentation.isEnabledAndVisible = project != null && dir != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val targetDir = resolveTargetDirectory(e) ?: return

        val rawInput = Messages.showInputDialog(
            project,
            "请输入 BPMN 流程文件名（不含扩展名）：",
            "新建 BPMN Process",
            Messages.getQuestionIcon(),
            "process",
            object : com.intellij.openapi.ui.InputValidator {
                override fun checkInput(inputString: String?): Boolean {
                    val s = inputString?.trim().orEmpty()
                    if (s.isEmpty()) return false
                    // 简单校验：不能包含路径分隔符等非法字符
                    val illegal = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
                    return illegal.none { it in s }
                }

                override fun canClose(inputString: String?): Boolean = checkInput(inputString)
            }
        ) ?: return

        val baseName = rawInput.trim().removeSuffix(".bpmn")
        if (baseName.isEmpty()) return

        val fileName = "$baseName.bpmn"
        val existing = targetDir.findChild(fileName)
        if (existing != null) {
            Messages.showErrorDialog(
                project,
                "目录中已存在同名文件：$fileName",
                "新建 BPMN Process"
            )
            return
        }

        val processId = toProcessId(baseName)
        val content = bpmnTemplate(processId, baseName)

        val createdFile: VirtualFile? = try {
            WriteCommandAction.writeCommandAction(project)
                .withName("Create BPMN Process")
                .compute<VirtualFile, Throwable> {
                    val newFile = targetDir.createChildData(this, fileName)
                    newFile.setBinaryContent(content.toByteArray(Charsets.UTF_8))
                    newFile
                }
        } catch (t: Throwable) {
            LOG.error("Failed to create BPMN file in ${targetDir.path}", t)
            Messages.showErrorDialog(
                project,
                "创建文件失败：${t.message}",
                "新建 BPMN Process"
            )
            null
        }

        val vf = createdFile ?: return
        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
    }

    /**
     * 解析当前用户操作所对应的目标目录。优先使用 IDE_VIEW（项目树视图）选中的目录，
     * 其次回退到 VIRTUAL_FILE 数据键对应的目录或其父目录。
     */
    private fun resolveTargetDirectory(e: AnActionEvent): VirtualFile? {
        val ideView = e.getData(LangDataKeys.IDE_VIEW)
        val fromIdeView = ideView?.orChooseDirectory?.virtualFile
        if (fromIdeView != null && fromIdeView.isDirectory) return fromIdeView

        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return if (vf.isDirectory) vf else vf.parent
    }
}
