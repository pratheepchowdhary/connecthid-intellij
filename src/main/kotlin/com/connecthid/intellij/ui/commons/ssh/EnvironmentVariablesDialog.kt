// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.connecthid.intellij.ui.commons.ssh

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.EnvVariablesTable
import com.intellij.execution.util.EnvironmentVariable
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.ui.table.JBTable
import com.intellij.util.ArrayUtil
import com.intellij.util.EnvironmentUtil
import com.intellij.util.io.IdeUtilIoBundle
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.*
import java.util.function.Consumer
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class EnvironmentVariablesDialog(
    private val myParent: EnvironmentVariablesTextFieldWithBrowseButton,
    systemVariables:MutableMap<String, String> = GeneralCommandLine().getParentEnvironment(),
    private val myAlwaysIncludeSystemVars: Boolean = false
) : DialogWrapper(
    myParent, true
) {
    private val myUserTable: EnvVariablesTable
    private val mySystemTable: EnvVariablesTable
    private val myIncludeSystemVarsCb: JCheckBox?
    private val myWholePanel: JPanel

    init {
        val userMap: MutableMap<String, String> = LinkedHashMap<String, String>(myParent.envs)
        val parentMap: MutableMap<String, String> =
            TreeMap<String, String>(systemVariables)

        myParent.myParentDefaults.putAll(parentMap)

        val userList: MutableList<EnvironmentVariable> =
            EnvironmentVariablesTextFieldWithBrowseButton.convertToVariables(userMap, false)
        val systemList: MutableList<EnvironmentVariable> =
            EnvironmentVariablesTextFieldWithBrowseButton.convertToVariables(parentMap, true)
        myUserTable = createEnvVariablesTable(userList, true)

        mySystemTable = createEnvVariablesTable(systemList, false,false)

        if (!myAlwaysIncludeSystemVars) {
            myIncludeSystemVarsCb = JCheckBox(ExecutionBundle.message("env.vars.system.include.title"))
            myIncludeSystemVarsCb.setSelected(myParent.isPassParentEnvs)
            myIncludeSystemVarsCb.addActionListener(object : ActionListener {
                override fun actionPerformed(e: ActionEvent?) {
                    updateSysTableState()
                }
            })
        } else {
            myIncludeSystemVarsCb = null
        }
        val label = JLabel(ExecutionBundle.message("env.vars.user.title"))
        label.setLabelFor(myUserTable.getTableView().getComponent())

        myWholePanel = JPanel(MigLayout("fill, ins 0, gap 0, hidemode 3"))
        myWholePanel.add(label, "hmax pref, wrap")
        myWholePanel.add(myUserTable.getComponent(), "push, grow, wrap, gaptop 5")
        val tablesSeparator: JComponent =
            if (myIncludeSystemVarsCb != null) myIncludeSystemVarsCb else JLabel(ExecutionBundle.message("env.vars.system.title"))
        myWholePanel.add(tablesSeparator, "hmax pref, wrap, gaptop 5")
        myWholePanel.add(mySystemTable.getComponent(), "push, grow, wrap, gaptop 5")

        updateSysTableState()
        setTitle(ExecutionBundle.message("environment.variables.dialog.title"))
        init()
    }

    protected fun createEnvVariablesTable(
        variables: MutableList<EnvironmentVariable>,
        userList: Boolean,
        tableWriteTable: Boolean = true
    ): MyEnvVariablesTable {
        return MyEnvVariablesTable(variables, userList,tableWriteTable)
    }

    override fun getInitialSize(): Dimension? {
        val size = super.getInitialSize()
        if (size != null) {
            return size
        }

        return Dimension(500, 500)
    }

    override fun getDimensionServiceKey(): String? {
        return "EnvironmentVariablesDialog"
    }

    private fun updateSysTableState() {
        mySystemTable.getTableView().setEnabled(this.isIncludeSystemVars)
        val userList: MutableList<EnvironmentVariable> =
            ArrayList<EnvironmentVariable>(myUserTable.getEnvironmentVariables())
        val systemList: MutableList<EnvironmentVariable> =
            ArrayList<EnvironmentVariable>(mySystemTable.getEnvironmentVariables())
        val dirty = booleanArrayOf(false)
        if (this.isIncludeSystemVars) {
            //System properties are included so overridden properties should be shown as 'bold' or 'modified' in a system table
            val iterator = userList.iterator()
            while (iterator.hasNext()) {
                val userVariable = iterator.next()
                val optional =
                    systemList.stream()
                        .filter { systemVariable: EnvironmentVariable? -> systemVariable!!.getName() == userVariable.getName() }
                        .findAny()
                optional.ifPresent(Consumer { variable: EnvironmentVariable? ->
                    if (variable!!.getValue() != userVariable.getValue()) {
                        variable.setValue(userVariable.getValue())
                        iterator.remove()
                        dirty[0] = true
                    }
                })
            }
        } else {
            // Overridden system properties should be shown as user variables as soon as system ones aren't included
            // Thus system table should look unmodified and disabled
            for (systemVariable in systemList) {
                if (myParent.isModifiedSysEnv(systemVariable)) {
                    val optional =
                        userList.stream()
                            .filter { userVariable: EnvironmentVariable? -> userVariable!!.getName() == systemVariable.getName() }
                            .findAny()
                    if (optional.isPresent()) {
                        optional.get().setValue(systemVariable.getValue())
                    } else {
                        val clone = systemVariable.clone()
                        clone.IS_PREDEFINED = false
                        userList.add(clone)
                        systemVariable.setValue(myParent.myParentDefaults.get(systemVariable.getName()))
                    }
                    dirty[0] = true
                }
            }
        }
        if (dirty[0]) {
            myUserTable.setValues(userList)
            mySystemTable.setValues(systemList)
        }
    }

    override fun createCenterPanel(): JComponent {
        return myWholePanel
    }

    override fun doValidate(): ValidationInfo? {
        for (variable in myUserTable.getEnvironmentVariables()) {
            val name = variable.getName()
            val value = variable.getValue()
            if (StringUtil.isEmpty(name) && StringUtil.isEmpty(value)) continue

            if (!EnvironmentUtil.isValidName(name)) {
                return ValidationInfo(IdeUtilIoBundle.message("run.configuration.invalid.env.name", name))
            }
            if (!EnvironmentUtil.isValidValue(value)) {
                return ValidationInfo(IdeUtilIoBundle.message("run.configuration.invalid.env.value", name, value))
            }
        }
        return super.doValidate()
    }

    override fun doOKAction() {
        myUserTable.stopEditing()
        val envs: MutableMap<String?, String?> = LinkedHashMap<String?, String?>()
        for (variable in myUserTable.getEnvironmentVariables()) {
            if (StringUtil.isEmpty(variable.getName()) && StringUtil.isEmpty(variable.getValue())) continue
            envs.put(variable.getName(), variable.getValue())
        }
        for (variable in mySystemTable.getEnvironmentVariables()) {
            if (myParent.isModifiedSysEnv(variable)) {
                envs.put(variable.getName(), variable.getValue())
            }
        }
        myParent.envs = envs
        myParent.isPassParentEnvs = this.isIncludeSystemVars
        super.doOKAction()
    }

    private val isIncludeSystemVars: Boolean
        get() = myAlwaysIncludeSystemVars || myIncludeSystemVarsCb != null && myIncludeSystemVarsCb.isSelected()

    protected inner class MyEnvVariablesTable(
        list: MutableList<EnvironmentVariable>?,
        protected val myUserList: Boolean,
        val tableWriteTable: Boolean = true
    ) : EnvVariablesTable() {
        init {
            val tableView = getTableView()
            tableView.setVisibleRowCount(JBTable.PREFERRED_SCROLLABLE_VIEWPORT_HEIGHT_IN_ROWS)
            setValues(list)
            setPasteActionEnabled(myUserList)
        }

        override fun createAddAction(): AnActionButtonRunnable? {
            return if (myUserList) super.createAddAction() else null
        }

        override fun createRemoveAction(): AnActionButtonRunnable? {
            return if (myUserList) super.createRemoveAction() else null
        }

        override fun createExtraToolbarActions(): Array<AnAction?> {
            return if (myUserList) super.createExtraToolbarActions() else ArrayUtil.append<AnAction?>(
                super.createExtraToolbarActions(),
                object : DumbAwareAction(
                    ActionsBundle.message("action.ChangesView.Revert.text"),
                    null,
                    AllIcons.Actions.Rollback
                ) {
                    override fun actionPerformed(e: AnActionEvent) {
                        stopEditing()
                        val variables = getSelection()
                        for (environmentVariable in variables) {
                            if (myParent.isModifiedSysEnv(environmentVariable)) {
                                environmentVariable.setValue(myParent.myParentDefaults.get(environmentVariable.getName()))
                                setModified()
                            }
                        }
                        getTableView().revalidate()
                        getTableView().repaint()
                    }

                    override fun update(e: AnActionEvent) {
                        val selection = getSelection()
                        for (variable in selection) {
                            if (myParent.isModifiedSysEnv(variable)) {
                                e.getPresentation().setEnabled(true)
                                return
                            }
                        }
                        e.getPresentation().setEnabled(false)
                    }

                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }
                })
        }

        override fun createListModel(): ListTableModel<EnvironmentVariable> {
            return ListTableModel<EnvironmentVariable>(
                MyNameColumnInfo(), MyValueColumnInfo(tableWriteTable)
            )
        }

        protected inner class MyNameColumnInfo : NameColumnInfo() {
            private val myModifiedRenderer: DefaultTableCellRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component {
                    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    component.setEnabled(table.isEnabled() && (hasFocus || isSelected))
                    return component
                }
            }

            override fun getCustomizedRenderer(
                o: EnvironmentVariable,
                renderer: TableCellRenderer?
            ): TableCellRenderer? {
                return if (o.getNameIsWriteable()) renderer else myModifiedRenderer
            }
        }

        protected inner class MyValueColumnInfo(val tableWriteTable: Boolean) : ValueColumnInfo() {
            private val myModifiedRenderer: DefaultTableCellRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable?,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component {
                    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    component.setFont(component.getFont().deriveFont(Font.BOLD))
                    if (!hasFocus && !isSelected) {
                        component.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED)
                    }
                    return component
                }
            }

            override fun isCellEditable(environmentVariable: EnvironmentVariable?): Boolean {
                return tableWriteTable
            }

            override fun getCustomizedRenderer(
                o: EnvironmentVariable,
                renderer: TableCellRenderer?
            ): TableCellRenderer? {
                return if (myParent.isModifiedSysEnv(o)) myModifiedRenderer else renderer
            }
        }
    }
}
