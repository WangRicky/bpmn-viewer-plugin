package com.rickytech.bpmn.viewer

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class BpmnFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.extension?.lowercase() == "bpmn"
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return BpmnFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = "bpmn-viewer"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
