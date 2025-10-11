// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.models.FileTask
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.UnknownRunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBList.StripedListCellRenderer
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.SmartList
import com.intellij.util.containers.CollectionFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.*
import javax.swing.JCheckBox
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

/**
 * @author Pratheep Kanati
 */
class FilesPickerPanel(private val myListener: FilesAddListener) : JPanel() {
    private val myShowSettingsBeforeRunCheckBox: JCheckBox
    private val myActivateToolWindowBeforeRunCheckBox: JCheckBox
    private val myFocusToolWindowBeforeRunCheckBox: JCheckBox
    private val myList: JBList<FileTask>
    private val myModel: CollectionListModel<FileTask>
    private var myRunConfiguration: RunConfiguration? = null


    private val originalTasks: MutableList<FileTask> = SmartList<FileTask>()
    private val myPanel: JPanel

    private val clonedTasks: MutableSet<FileTask> =
        CollectionFactory.createSmallMemoryFootprintSet<FileTask>()

    init {
        myModel = CollectionListModel<FileTask>()
        myList = JBList<FileTask>(myModel)
        myList.getEmptyText().setText(ExecutionBundle.message("before.launch.panel.empty"))
        myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        myList.setCellRenderer(MyListCellRenderer())
        myList.setVisibleRowCount(4)

        myModel.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent?) {
                updateText()
            }

            override fun intervalRemoved(e: ListDataEvent?) {
                updateText()
            }

            override fun contentsChanged(e: ListDataEvent?) {
            }
        })

        val myDecorator = ToolbarDecorator.createDecorator<FileTask?>(myList)

        myDecorator.setEditAction(object : AnActionButtonRunnable {
            override fun run(button: AnActionButton) {
                updateText()
            }
        })
        myDecorator.setEditActionUpdater(object : AnActionButtonUpdater {
            override fun isEnabled(e: AnActionEvent): Boolean {
               return  true
            }
        })
        myDecorator.setAddAction(object : AnActionButtonRunnable {
            override fun run(button: AnActionButton) {
                doAddAction(button)
            }
        })
        myDecorator.setAddActionUpdater(object : AnActionButtonUpdater {
            override fun isEnabled(e: AnActionEvent): Boolean {
                return checkBeforeRunTasksAbility(true)
            }
        })

        myDecorator.setRemoveAction(object : AnActionButtonRunnable {
            override fun run(button: AnActionButton?) {
                ListUtil.removeSelectedItems<FileTask>(myList)
            }
        })

        myShowSettingsBeforeRunCheckBox = JCheckBox(ExecutionBundle.message("configuration.edit.before.run"))
        myShowSettingsBeforeRunCheckBox.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent?) {
                updateText()
            }
        })
        myActivateToolWindowBeforeRunCheckBox =
            JCheckBox(ExecutionBundle.message("configuration.activate.toolwindow.before.run"))
        myFocusToolWindowBeforeRunCheckBox =
            JCheckBox(ExecutionBundle.message("configuration.focus.toolwindow.before.run"))

        myPanel = myDecorator.createPanel()
        myDecorator.getActionsPanel().setCustomShortcuts(
            CommonActionsPanel.Buttons.EDIT,
            CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.EDIT),
            CommonShortcuts.DOUBLE_CLICK_1
        )


        setLayout(BorderLayout())
        add(myPanel, BorderLayout.CENTER)
        val checkboxPanel = JPanel(FlowLayout(FlowLayout.LEADING, scale(10), scale(5)))
        checkboxPanel.add(myShowSettingsBeforeRunCheckBox)
        checkboxPanel.add(myActivateToolWindowBeforeRunCheckBox)
        checkboxPanel.add(myFocusToolWindowBeforeRunCheckBox)
        add(checkboxPanel, BorderLayout.SOUTH)
    }

    override fun setVisible(aFlag: Boolean) {
        super.setVisible(aFlag)
        updateText()
    }



    fun doReset(settings: RunnerAndConfigurationSettings) {
        clonedTasks.clear()

        myRunConfiguration = settings.getConfiguration()

        originalTasks.clear()
        //todo
       // originalTasks.addAll(RunManagerImplKt.doGetBeforeRunTasks(myRunConfiguration))
        myModel.replaceAll(originalTasks)
        myShowSettingsBeforeRunCheckBox.setSelected(settings.isEditBeforeRun())
        myShowSettingsBeforeRunCheckBox.setEnabled(!this.isUnknown)
        myActivateToolWindowBeforeRunCheckBox.setSelected(settings.isActivateToolWindowBeforeRun())
        myActivateToolWindowBeforeRunCheckBox.setEnabled(!this.isUnknown)
        myFocusToolWindowBeforeRunCheckBox.setSelected(settings.isFocusToolWindowBeforeRun())
        myFocusToolWindowBeforeRunCheckBox.setEnabled(!this.isUnknown)
        myPanel.setVisible(checkBeforeRunTasksAbility(false))
        updateText()
    }

    private fun updateText() {
        val count = myModel.getSize()
        val title = ExecutionBundle.message("before.launch.panel.title")
        val suffix =
            if (count == 0 || isVisible()) "" else ExecutionBundle.message("before.launch.panel.title.suffix", count)
        //myListener.titleChanged(title + suffix)
    }



    fun needEditBeforeRun(): Boolean {
        return myShowSettingsBeforeRunCheckBox.isSelected()
    }

    fun needActivateToolWindowBeforeRun(): Boolean {
        return myActivateToolWindowBeforeRunCheckBox.isSelected()
    }

    fun needFocusToolWindowBeforeRun(): Boolean {
        return myFocusToolWindowBeforeRunCheckBox.isSelected()
    }

    private fun checkBeforeRunTasksAbility(checkOnlyAddAction: Boolean): Boolean {
        if (this.isUnknown) {
            return false
        }



        return false
    }

    private val isUnknown: Boolean
        get() = myRunConfiguration is UnknownRunConfiguration

    private fun doAddAction(button: AnActionButton) {
        if (this.isUnknown) {
            return
        }

        val activeProviderKeys =
            this.activeProviderKeys
        val actionGroup = DefaultActionGroup()
        val dataContext = SimpleDataContext.builder()
            .add<Project?>(CommonDataKeys.PROJECT, myRunConfiguration!!.getProject())
            .add<JPanel?>(PlatformCoreDataKeys.CONTEXT_COMPONENT, myPanel)
            .build()
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            ExecutionBundle.message("add.new.before.run.task.name"), actionGroup,
            dataContext, false, false, false, null,
            -1, Conditions.alwaysTrue<AnAction?>()
        )
        popup.show(Objects.requireNonNull<RelativePoint>(button.getPreferredPopupPoint()))
    }



    fun addTask(task: FileTask) {
        myModel.add(task)
    }

    fun replaceTasks(tasks: MutableList<FileTask>) {
        myModel.replaceAll(tasks)
    }

    private val activeProviderKeys: MutableSet<Key<*>?>
        get() {
            val items: MutableList<FileTask> = myModel.getItems()
            val result =
                CollectionFactory.createSmallMemoryFootprintSet<Key<*>?>(
                    items.size
                )
            for (task in items) {
               // result.add(task.getProviderId())
            }
            return result
        }



    interface FilesAddListener {


        fun onFileAdded(fileTask: FileTask)
    }

    private inner class MyListCellRenderer : StripedListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<Any>,
            value: Any,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            setIcon(AllIcons.Webreferences.Server)
            setText("testpath")
            return this
        }
    }
}