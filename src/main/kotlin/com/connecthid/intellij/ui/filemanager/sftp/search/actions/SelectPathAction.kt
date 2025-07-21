// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.connecthid.intellij.ui.filemanager.sftp.search.actions

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

class SelectPathAction(val preSelectedPath: String,  val onFolderSuggestions:(String)-> Unit, val onChanged: (String) -> Unit) : AnAction(), CustomComponentAction {
  private val latestMaskProperty: AtomicProperty<String?> = AtomicProperty(preSelectedPath)
  private var comboboxActionComponent : ComboboxActionComponent? = null
    val model = DefaultComboBoxModel<String>().apply {
    addElement(preSelectedPath)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): ComboboxActionComponent {
    comboboxActionComponent = ComboboxActionComponent(preSelectedPath, latestMaskProperty,onFolderSuggestions) {
      onChanged(latestMaskProperty.get()?:preSelectedPath)
    }.also { it.isEditable = true }
    comboboxActionComponent!!.model = model
    return comboboxActionComponent!!
  }

    fun hidePopup(){
        if(comboboxActionComponent != null) {
            if(comboboxActionComponent!!.isPopupVisible){
                comboboxActionComponent!!.hidePopup()
            }
        }
    }
    fun showPopup() {
        if(comboboxActionComponent != null) {
            comboboxActionComponent!!.selectedItem = latestMaskProperty.get()?:preSelectedPath
            (comboboxActionComponent!!.editor.editorComponent as JTextField).text = latestMaskProperty.get()?:preSelectedPath
            if(!comboboxActionComponent!!.isPopupVisible){
                comboboxActionComponent!!.showPopup()
            }
        }
    }


  override fun actionPerformed(e: AnActionEvent) {}

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val comboboxComponent = component as ComboboxActionComponent
    comboboxComponent.selectedItem = latestMaskProperty.get()?:preSelectedPath
  }


   class ComboboxActionComponent(val selectedPath: String,
                                private val latestMaskProperty: AtomicProperty<String?>,
                                 val onFolderSuggestions:(String)-> Unit,
                                private val onChanged: () -> Unit) :
    ComboBox<String>(){
        // make below higher order functin return boolean
        private val rebuild: () -> Boolean = {
            println("rebuild called latestMaskProperty=${latestMaskProperty.get()} getNormalizedText=${getNormalizedText()}")
            if (latestMaskProperty.get()!=null && latestMaskProperty.get().equals(getNormalizedText())) {
                println("rebuild false")
                false
            }
            latestMaskProperty.set(getNormalizedText())
            println("rebuild true")
            onChanged()
            true
        }
       private var lastSuggestionCalled : String=""

       init {
      setEditor(BasicComboBoxEditor())
      maximumRowCount = 12
      prototypeDisplayValue = selectedPath
      isOpaque = false
      insertItemAt(selectedPath, 0)
      addItemListener { rebuild() }

      (editor.editorComponent as JTextField).also {
        it.background = JBUI.CurrentTheme.BigPopup.searchFieldBackground()
        it.addFocusListener(object : FocusAdapter() {
          override fun focusGained(e: FocusEvent) {
             showPopup()
          }

          override fun focusLost(e: FocusEvent) {
            if (it.text.isEmpty()) {
              (editor.editorComponent as JTextField).text = selectedPath
              selectedIndex = 0
            }
          }
        })

        it.document.addDocumentListener(object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
              if(rebuild()){
                  updateSuggestions()
              }
          }
        })
      }
    }

    override fun getPreferredSize(): Dimension {
      val editorField = editor.editorComponent as JTextField
      val text = editorField.text.ifEmpty { selectedPath }
      val fontMetrics = editorField.getFontMetrics(editorField.font)
      val textWidth = fontMetrics.stringWidth(text)

      // Add some padding to the text width and ensure a minimum width
      val width = Math.max(textWidth + JBUI.scale(60), JBUI.scale(200))

      return Dimension(width, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height + insets.top + insets.bottom)
    }

    private fun getNormalizedText(): String? {
      val editorField = editor.editorComponent as JTextField
      return  editorField.text
    }

    private fun updateSuggestions() {
      val  text = (editor.editorComponent as JTextField).text
      if(text.endsWith("/") && !lastSuggestionCalled.equals(text)){
         println("updateSuggestions$text")
          lastSuggestionCalled = text
          onFolderSuggestions(text)
      }
    }
  }


}