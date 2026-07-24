package com.rickytech.bpmn.viewer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class BpmnFileType : FileType {
    companion object {
        @JvmField
        val INSTANCE = BpmnFileType()

        private val ICON: Icon = IconLoader.getIcon("/icons/bpmn.svg", BpmnFileType::class.java)
    }
    override fun getName() = "BPMN"
    override fun getDescription() = "BPMN Process Definition"
    override fun getDefaultExtension() = "bpmn"
    override fun getIcon(): Icon = ICON
    override fun isBinary() = false
    override fun isReadOnly() = true
}
