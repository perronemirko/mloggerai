package com.mloggerai;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;

import javax.swing.*;
import java.awt.*;

public class ErrorSolverToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        // Text area per i log
        JBTextArea textArea = new JBTextArea();
        textArea.setEditable(false);

        // Scroll pane
        JBScrollPane scrollPane = new JBScrollPane(textArea);

        // Panel principale con BorderLayout
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        // Panel per il bottone in cima
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearButton = new JButton("Clear");
        clearButton.setSize(clearButton.getSize().width/2,clearButton.getSize().height/2);
        JCheckBox activateDebug = new JCheckBox("Debug");
        JCheckBox verbose = new JCheckBox("Verbose");
        buttonPanel.add(verbose);
//        buttonPanel.add(activateDebug);
        buttonPanel.add(clearButton);

        // Aggiunge il panel dei bottoni sopra la textArea
        panel.add(buttonPanel, BorderLayout.NORTH);

        // Azione del bottone: azzera la textArea
        clearButton.addActionListener(e -> textArea.setText(""));

        // Creazione del Content e aggiunta al Tool Window
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
//        verbose.setSelected(true);
        // Inizializza il servizio che cattura i log
        ErrorSolverService.initGpt(project, textArea, activateDebug,verbose);
    }
}
