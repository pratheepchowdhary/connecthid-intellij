// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.connecthid.intellij.ui.filemanager.sftp.search.actions

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.performDumbAwareWithCallbacks
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.util.function.IntPredicate
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke

abstract class ServerChooserAction (project: Project): ActionGroup(), CustomComponentAction, DumbAware,
    SearchEverywhereToggleAction {
        // Use lazy initialization to defer service access until actually needed
        val sshService by lazy { getSSHService() }

    init {
        setPopup(true)
        getTemplatePresentation().setPerformGroup(true)
    }

    protected abstract fun onServerSelected(o: Server)

    protected abstract val selectedSever: Server

    protected abstract fun onProjectServerToggled()


    override fun getChildren(e: AnActionEvent?): Array<AnAction?> {
        return EMPTY_ARRAY
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val component: JComponent =
            ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
        component.putClientProperty(
            MnemonicHelper.MNEMONIC_CHECKER,
            IntPredicate { keyCode: Int ->
                KeyEvent.getExtendedKeyCodeForChar(
                    TOGGLE.code
                ) == keyCode ||
                        KeyEvent.getExtendedKeyCodeForChar(CHOOSE.code) == keyCode
            })

        MnemonicHelper.registerMnemonicAction(component, CHOOSE.code)
        val map = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val mask = MnemonicHelper.getFocusAcceleratorKeyMask()
        map.put(KeyStroke.getKeyStroke(TOGGLE.code, mask, false), TOGGLE_ACTION_NAME)
        component.getActionMap().put(TOGGLE_ACTION_NAME, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                // mimic AnAction event invocation to trigger myEverywhereAutoSet=false logic
                val dataContext = DataManager.getInstance().getDataContext(component)
                val inputEvent = KeyEvent(
                    component, KeyEvent.KEY_PRESSED, e.getWhen(), MnemonicHelper.getFocusAcceleratorKeyMask(),
                    KeyEvent.getExtendedKeyCodeForChar(TOGGLE.code), TOGGLE
                )
                val event =  AnActionEvent.createEvent(
                    this@ServerChooserAction,
                    dataContext,
                    null,
                    ActionPlaces.TOOLBAR,
                    ActionUiKind.Companion.NONE,
                    inputEvent
                )
                performDumbAwareWithCallbacks(
                    this@ServerChooserAction,
                    event,
                    Runnable { this@ServerChooserAction.onProjectServerToggled() })
            }
        })
        if (ExperimentalUI.isNewUI()) {
            component.setBorder(JBUI.Borders.emptyRight(8))
        }
        return component
    }

    override fun update(e: AnActionEvent) {
        val selection = this.selectedSever
        val name = StringUtil.trimMiddle(StringUtil.notNullize(selection.stmpName), 30)
        val text = StringUtil.escapeMnemonics(name).replaceFirst(("(?i)([" + TOGGLE + CHOOSE + "])").toRegex(), "_$1")
        e.getPresentation().setText(text)
       // e.getPresentation().setIcon(OffsetIcon.getOriginalIcon(selection.getIcon()))
        val shortcutText = KeymapUtil.getKeystrokeText(
            KeyStroke.getKeyStroke(
                CHOOSE.code, MnemonicHelper.getFocusAcceleratorKeyMask(), true
            )
        )
        val shortcutText2 = KeymapUtil.getKeystrokeText(
            KeyStroke.getKeyStroke(
                TOGGLE.code, MnemonicHelper.getFocusAcceleratorKeyMask(), true
            )
        )
        e.getPresentation().setDescription(
            LangBundle.message(
                "action.choose.scope.p.toggle.scope.description",
                shortcutText,
                shortcutText2
            )
        )
        val button = e.getPresentation().getClientProperty<JComponent?>(CustomComponentAction.COMPONENT_KEY)
        if (button != null) {
            //button.setBackground(selection.getColor())
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val button = e.getPresentation().getClientProperty<JComponent?>(CustomComponentAction.COMPONENT_KEY)
        if (button == null || !button.isValid()) return
        val items = sshService.getSavedConnections()
        val step: BaseListPopupStep<Server> = object : BaseListPopupStep<Server>("", items) {
            override fun onChosen(selectedValue: Server, finalChoice: Boolean): PopupStep<*>? {
                onServerSelected(selectedValue)
                val toolbar = ActionToolbar.findToolbarBy(button)
                if (toolbar != null) toolbar.updateActionsImmediately()
                return FINAL_CHOICE
            }

            override fun isSpeedSearchEnabled(): Boolean {
                return true
            }

            override fun getTextFor(value: Server): String {
                return StringUtil.notNullize(value.stmpName)
            }

            override fun getIconFor(value: Server): Icon? {
                return null
            }

            override fun isSelectable(value: Server): Boolean {
                return true
            }
        }
        val selection = this.selectedSever
        step.setDefaultOptionIndex(
            ContainerUtil.indexOf<Server>(
                items,
                Condition { o: Server -> o.stmpName == selection.stmpName})
        )
        val popup = ListPopupImpl(e.getProject(), step)
        popup.setMaxRowCount(10)
        popup.showUnderneathOf(button)
    }

    companion object {
        const val CHOOSE: Char = 'O'
        const val TOGGLE: Char = 'P'
        const val TOGGLE_ACTION_NAME: String = "toggleProjectScope"
    }
}