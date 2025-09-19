package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.PluginBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel


class ConnectHIDSettingsEditor(val project: Project, task: RunConfigurationTask) : SettingsEditor<ConnectHIDRunConfiguration>() {
    private var myPanel: JPanel?=null
    private var myScriptPathPanel: JPanel? = null
    private var myScriptTextPanel: JPanel? = null
    private var myScript: RawCommandLineEditor? = null
    private var myScriptSelector: TextFieldWithBrowseButton? = null
    private var myScriptOptions: RawCommandLineEditor? = null
    private var myScriptFileWorkingDirectory: TextFieldWithBrowseButton? = null
    private var myScriptWorkingDirectory: TextFieldWithBrowseButton? = null
    private var myInterpreterSelector: TextFieldWithBrowseButton? = null
    private var myInterpreterOptions: RawCommandLineEditor? = null
    private var myExecuteFileInTerminal: JBCheckBox? = null
    private var myExecuteScriptInTerminal: JBCheckBox? = null
    private var myEnvComponent: EnvironmentVariablesComponent? = null
    private var myScriptEnvComponent: EnvironmentVariablesComponent? = null
    private var myScriptGroup: ButtonGroup
    private var myScriptFileRadioButton: JBRadioButton? = null
    private var myScriptTextRadioButton: JBRadioButton? = null

    init {
        myScriptSelector!!.addBrowseFolderListener(
            project, FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle(PluginBundle.message("sh.label.choose.shell.script"))
        )
        myScriptFileWorkingDirectory!!.addBrowseFolderListener(
            project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(PluginBundle.message("sh.label.choose.script.working.directory"))
        )
        myScriptWorkingDirectory!!.addBrowseFolderListener(
            project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(PluginBundle.message("sh.label.choose.script.working.directory"))
        )
        myInterpreterSelector!!.addBrowseFolderListener(
            project, FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle(PluginBundle.message("sh.label.choose.interpreter"))
        )

        myScriptGroup = ButtonGroup()
        myScriptGroup.add(myScriptTextRadioButton)
        myScriptGroup.add(myScriptFileRadioButton)
        myScriptFileRadioButton!!.addActionListener(ActionListener { action: ActionEvent? -> selectMode() })
        myScriptTextRadioButton!!.addActionListener(ActionListener { action: ActionEvent? -> selectMode() })
    }

    override fun resetEditorFrom(configuration: ConnectHIDRunConfiguration) {

        if (configuration.isExecuteScriptFile()) {
            myScriptFileRadioButton!!.setSelected(true);
        } else {
            myScriptTextRadioButton!!.setSelected(true);
        }
        selectMode();
        // Configure UI for script text execution
        myScript!!.setText(configuration.getScriptText());
        myScriptWorkingDirectory!!.setText(configuration.getScriptWorkingDirectory());
        myExecuteScriptInTerminal!!.setSelected(configuration.isExecuteInTerminal());
        myScriptEnvComponent!!.setEnvData(configuration.getEnvData());

        // Configure script by path execution
        myScriptSelector!!.setText(configuration.getScriptPath())
        myScriptOptions!!.setText(configuration.getScriptOptions())
        myScriptFileWorkingDirectory!!.setText(configuration.getScriptWorkingDirectory())
        myInterpreterSelector!!.setText(configuration.getInterpreterPath());
        myInterpreterOptions!!.setText(configuration.getInterpreterOptions());
        myExecuteFileInTerminal!!.setSelected(configuration.isExecuteInTerminal());
        myEnvComponent!!.setEnvData(configuration.getEnvData());

    }

    override fun applyEditorTo(configuration: ConnectHIDRunConfiguration) {
        configuration.setScriptText(myScript!!.getText());
        // If we execute script by path, fill one components or other components if execute script text
        if (myScriptFileRadioButton!!.isSelected()) {
            configuration.setScriptWorkingDirectory(myScriptFileWorkingDirectory!!.getText());
            configuration.setExecuteInTerminal(myExecuteFileInTerminal!!.isSelected());
            configuration.setEnvData(myEnvComponent!!.getEnvData());
            configuration.setExecuteScriptFile(true);
        } else {
            configuration.setScriptWorkingDirectory(myScriptWorkingDirectory!!.getText());
            configuration.setExecuteInTerminal(myExecuteScriptInTerminal!!.isSelected());
            configuration.setEnvData(myScriptEnvComponent!!.getEnvData());
            configuration.setExecuteScriptFile(false);
        }
        configuration.setScriptPath(myScriptSelector!!.getText());
        configuration.setScriptOptions(myScriptOptions!!.getText());
        configuration.setInterpreterPath(myInterpreterSelector!!.getText());
        configuration.setInterpreterOptions(myInterpreterOptions!!.getText());
    }
    private fun selectMode()
    {
        val scriptExecutionSelected = myScriptFileRadioButton!!.isSelected()
        myScriptFileRadioButton!!.setSelected(scriptExecutionSelected)
        myScriptPathPanel!!.setVisible(scriptExecutionSelected)
        myScriptTextRadioButton!!.setSelected(!scriptExecutionSelected)
        myScriptTextPanel!!.setVisible(!scriptExecutionSelected)
    }

    override fun createEditor(): JComponent {
        return myPanel!!
    }
}