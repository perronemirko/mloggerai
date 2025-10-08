package com.mloggerai.plugin
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import javax.swing.JPanel

class ErrorSolverToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel()
        val textArea = JBTextArea()
        textArea.isEditable = false
        panel.add(JBScrollPane(textArea))

        toolWindow.component.add(panel)

        ErrorSolverService.init(project, textArea)
    }
}
