package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTask
import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Conditions
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBList.StripedListCellRenderer
import com.intellij.ui.scale.JBUIScale.scale
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.util.Objects
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class RunAfterTasksPanel(val taskModel: TaskModel): JPanel() {
    private val service = getSSHService()
    private val myList: JBList<TaskModel>
    private val myModel: CollectionListModel<TaskModel>
    private val myPanel: JPanel
    init {
        myModel = CollectionListModel<TaskModel>()
        myList = JBList<TaskModel>(myModel)
        myList.getEmptyText().setText(PluginBundle.message("after.launch.panel.empty"))
        myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        myList.setCellRenderer(MyListCellRenderer())
        myList.setVisibleRowCount(4)

        myModel.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent?) {
                //updateText()
            }

            override fun intervalRemoved(e: ListDataEvent?) {
                //updateText()
            }

            override fun contentsChanged(e: ListDataEvent?) {
            }
        })

        val myDecorator = ToolbarDecorator.createDecorator<TaskModel>(myList)

        myDecorator.setEditAction(object : AnActionButtonRunnable {
            override fun run(button: AnActionButton) {
                //updateText()
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
                //return checkBeforeRunTasksAbility(true)
                return true
            }
        })

        myDecorator.setRemoveAction(object : AnActionButtonRunnable {
            override fun run(button: AnActionButton?) {
                ListUtil.removeSelectedItems<TaskModel>(myList)
            }
        })



        myPanel = myDecorator.createPanel()
        myDecorator.getActionsPanel().setCustomShortcuts(
            CommonActionsPanel.Buttons.EDIT,
            CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.EDIT),
            CommonShortcuts.DOUBLE_CLICK_1
        )


        setLayout(BorderLayout())
        add(myPanel, BorderLayout.CENTER)
        val checkboxPanel = JPanel(FlowLayout(FlowLayout.LEADING, scale(10), scale(5)))
        add(checkboxPanel, BorderLayout.SOUTH)
    }

    fun setRunAfter(tasksIds: String){
        val tasks = tasksIds.split(";")
        val alltasks = service.getScripts()
        tasks.forEach { scriptId ->
            if(!scriptId.isEmpty()){
                val task = alltasks.firstOrNull(){it.scriptId == scriptId}
                if(task != null){
                    myModel.add(task)
                }
            }
        }
    }
    fun getTaskIds(): String{
        return myModel.items.joinToString(";") { it.scriptId }
    }

    private fun doAddAction(button: AnActionButton) {

        val actionGroup = DefaultActionGroup()

        service.getScripts().forEach {
            if(it.scriptId != taskModel.scriptId && !myModel.contains(it)){
              actionGroup.add(object : AnAction({ it.scriptName }, RunConfigurationTask.fromType(it.scriptType).icon){
                  override fun actionPerformed(p0: AnActionEvent) {
                      myModel.add(it)
                  }
              })
            }
        }

        val dataContext = SimpleDataContext.builder()
            .add<JPanel>(PlatformCoreDataKeys.CONTEXT_COMPONENT, myPanel)
            .build()
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            ExecutionBundle.message("add.new.before.run.task.name"), actionGroup,
            dataContext, false, false, false, null,
            -1, Conditions.alwaysTrue()
        )
        popup.show(Objects.requireNonNull(button.getPreferredPopupPoint()))
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
            val item = value as TaskModel
            setIcon(RunConfigurationTask.fromType(item.scriptType).icon)
            setText(item.scriptName)
            return this
        }
    }
}