package com.connecthid.intellij.ui.filemanager.sftp.search.actions

import com.intellij.find.FindBundle
import com.intellij.find.impl.RegExHelpPopup
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.ex.TooltipLinkProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.DumbAwareToggleAction
import javax.swing.Icon
import javax.swing.JComponent

sealed class TextSearchRightActionAction(val message: String,
                                         icon: Icon,
                                         hoveredIcon: Icon,
                                         selectedIcon: Icon,
                                         private var state: AtomicBooleanProperty,
                                         private val registerShortcut: (AnAction) -> Unit,
                                         private val callback: Runnable,
                                         private val myTooltipLink: TooltipLinkProvider.TooltipLink? = null) :
  DumbAwareToggleAction(message, null, icon), TooltipLinkProvider, TooltipDescriptionProvider {

  init {
    templatePresentation.hoveredIcon = hoveredIcon
    templatePresentation.selectedIcon = selectedIcon

      runInEdt {
          registerShortcut(this)
      }
  }

  fun getTooltip() = "${templatePresentation.text} ${KeymapUtil.getFirstKeyboardShortcutText(this)}"

  override fun getTooltipLink(owner: JComponent?): TooltipLinkProvider.TooltipLink? = myTooltipLink

  fun isSelected(): Boolean = state.get()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean = isSelected()

  override fun setSelected(e: AnActionEvent, selected: Boolean) {
    state.set(selected)
    callback.run()
  }

  class CaseSensitiveAction(property: AtomicBooleanProperty,
                            registerShortcut: (AnAction) -> Unit,
                            onChanged: Runnable) :
    TextSearchRightActionAction(
        FindBundle.message("find.popup.case.sensitive"),
                                AllIcons.Actions.MatchCase, AllIcons.Actions.MatchCaseHovered, AllIcons.Actions.MatchCaseSelected,
                                property, registerShortcut, onChanged)

  class WordAction(property: AtomicBooleanProperty,
                   registerShortcut: (AnAction) -> Unit,
                   onChanged: Runnable) :
    TextSearchRightActionAction(
        FindBundle.message("find.whole.words"),
                                AllIcons.Actions.Words, AllIcons.Actions.WordsHovered, AllIcons.Actions.WordsSelected,
                                property, registerShortcut, onChanged)

  class RegexpAction(property: AtomicBooleanProperty,
                     registerShortcut: (AnAction) -> Unit,
                     onChanged: Runnable) :
    TextSearchRightActionAction(
        FindBundle.message("find.regex"),
                                AllIcons.Actions.Regex, AllIcons.Actions.RegexHovered, AllIcons.Actions.RegexSelected,
                                property, registerShortcut, onChanged,
                                TooltipLinkProvider.TooltipLink(
                                    FindBundle.message("find.regex.help.link"),
                                                                RegExHelpPopup.createRegExLinkRunnable(null)))
}