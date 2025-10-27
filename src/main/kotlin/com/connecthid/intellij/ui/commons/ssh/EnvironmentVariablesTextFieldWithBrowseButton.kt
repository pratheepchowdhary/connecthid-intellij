// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.connecthid.intellij.ui.commons.ssh

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvFilesDialog
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.addEnvFile
import com.intellij.execution.util.EnvVariablesTable
import com.intellij.execution.util.EnvironmentVariable
import com.intellij.icons.AllIcons
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton.NoPathCompletion
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.UserActivityProviderComponent
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.KeyStroke
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import kotlin.math.min

open class EnvironmentVariablesTextFieldWithBrowseButton(val project: Project, val systemVariables:()-> MutableMap<String, String>) : NoPathCompletion(), UserActivityProviderComponent {
    var myData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT
    val myParentDefaults: MutableMap<String, String> = LinkedHashMap()
    private val myListeners: MutableList<ChangeListener> =
        ContainerUtil.createLockFreeCopyOnWriteList()
    private var myEnvFilePaths: MutableList<String> = ArrayList()
    private var myEnvFilesExtension: ExtendableTextComponent.Extension? = null
    private val myEnvFilePathsChangeListeners: MutableList<ChangeListener> =
        ContainerUtil.createLockFreeCopyOnWriteList()


    init {
        addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent?) {
               this@EnvironmentVariablesTextFieldWithBrowseButton.envs= EnvVariablesTable.parseEnvsFromText(getText())
                getEnvironmentVariablesAndLaunchDialog()
            }
        })
        getTextField().getDocument().addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!StringUtil.equals(this@EnvironmentVariablesTextFieldWithBrowseButton.envText, getText())) {
                    val textEnvs = EnvVariablesTable.parseEnvsFromText(getText())
                    myData = myData.with(textEnvs)
                    updateEnvFilesFromText()
                    fireStateChanged()
                }
            }
        })
        getTextField().getEmptyText().setText(ExecutionBundle.message("status.text.environment.variables"))
    }


    private fun addEnvFilesExtension() {
        if (myEnvFilesExtension != null) return
        myEnvFilesExtension = ExtendableTextComponent.Extension.create(
            AllIcons.General.OpenDisk, AllIcons.General.OpenDiskHover,
            ExecutionBundle.message("tooltip.browse.for.environment.files"), Runnable { browseForEnvFile() })
        getTextField().addExtension(myEnvFilesExtension!!)
        getTextField().getEmptyText().setText(ExecutionBundle.message("status.text.environment.variables.or.env.files"))
    }

    private fun browseForEnvFile() {
        if (myEnvFilePaths.isEmpty()) {
            addEnvFile(getTextField(), null) { s: String ->
                myEnvFilePaths.add(s)
                updateText()
                fireEnvFilePathsChanged()
                null
            }
        } else {
            val dialog = EnvFilesDialog(this, myEnvFilePaths)
            dialog.show()
            if (dialog.isOK()) {
                myEnvFilePaths = ArrayList(dialog.paths)
                updateText()
                fireEnvFilePathsChanged()
            }
        }
    }

    override fun getTextField(): ExtendableTextField {
        return super.getTextField() as ExtendableTextField
    }

    protected fun getEnvironmentVariablesAndLaunchDialog() {
        val systemizesVariables = runWithModalProgressBlocking(
            project = project,
            title = "Getting Environment Variables"
        ) {
            // Suspend the blocking call on IO dispatcher to avoid classloader/undispatched issues
            withContext(Dispatchers.IO) {
                systemVariables()  // Your SSH fetch: returns Map<String, String>
            }
        }

        // Show dialog on EDT (safe, but invokeLater for extra caution if nested)
        EnvironmentVariablesDialog(
            this,
            systemVariables = systemizesVariables,
            myAlwaysIncludeSystemVars = true
        ).show()
    }

    var envs: MutableMap<String?, String?>
        /**
         * @return unmodifiable Map instance
         */
        get() = myData.getEnvs()
        /**
         * @param envs Map instance containing user-defined environment variables
         * (iteration order should be reliable user-specified, like [LinkedHashMap] or [ImmutableMap])
         */
        set(envs) {
            this.data = myData.with(envs)
        }

    var data: EnvironmentVariablesData
        get() = myData
        set(data) {
            val oldData = myData
            myData = data
            updateText()
            if (oldData != data) {
                fireStateChanged()
            }
        }

    override fun getDefaultIcon(): Icon {
        return AllIcons.General.InlineVariables
    }

    override fun getHoveredIcon(): Icon {
        return AllIcons.General.InlineVariablesHover
    }

    private val envText: String
        get() {
            val s = stringifyEnvs(myData)
            if (myEnvFilePaths.isEmpty()) return s
            val buf = StringBuilder(s)
            for (path in myEnvFilePaths) {
                if (!buf.isEmpty()) {
                    buf.append(";")
                }
                buf.append(path)
            }
            return buf.toString()
        }

    private fun updateText() {
        setText(this.envText)
    }

    protected fun stringifyEnvs(evd: EnvironmentVariablesData): String {
        val buf = StringBuilder()
        for (entry in evd.getEnvs().entries) {
            if (!buf.isEmpty()) {
                buf.append(";")
            }
            buf.append(StringUtil.escapeChar(entry.key, ';'))
                .append("=")
                .append(StringUtil.escapeChar(entry.value, ';'))
        }
        return buf.toString()
    }

    var isPassParentEnvs: Boolean
        get() = myData.isPassParentEnvs()
        set(passParentEnvs) {
            this.data = myData.with(passParentEnvs)
        }

    override fun addChangeListener(changeListener: ChangeListener) {
        myListeners.add(changeListener)
    }

    fun addEnvFilePathsChangeListener(changeListener: ChangeListener) {
        myListeners.add(changeListener)
    }

    fun removeEnvFilePathsChangeListener(changeListener: ChangeListener) {
        myListeners.remove(changeListener)
    }

    override fun removeChangeListener(changeListener: ChangeListener) {
        myListeners.remove(changeListener)
    }

    private fun fireStateChanged() {
        for (listener in myListeners) {
            listener.stateChanged(ChangeEvent(this))
        }
    }

    private fun fireEnvFilePathsChanged() {
        for (listener in myEnvFilePathsChangeListeners) {
            listener.stateChanged(ChangeEvent(this))
        }
    }

    var envFilePaths: MutableList<String>
        get() = myEnvFilePaths
        set(paths) {
            myEnvFilePaths = ArrayList<String>(paths)
            this.data = myData
            addEnvFilesExtension()
            fireEnvFilePathsChanged()
        }

    private fun updateEnvFilesFromText() {
        val text = StringUtil.trimStart(getText(), stringifyEnvs(myData))
        if (myEnvFilePaths.isEmpty() || text.isEmpty()) return
        val paths = ContainerUtil.filter<String?>(
            ContainerUtil.map<String, String?>(
                text.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray(),
                Function { s: String -> s.trim { it <= ' ' } }), Condition { s: String? -> !s!!.isEmpty() })
        for (i in 0..<min(myEnvFilePaths.size, paths.size)) {
            myEnvFilePaths.set(i, paths.get(i))
        }
        fireEnvFilePathsChanged()
    }

    override fun getIconTooltip(): @NlsContexts.Tooltip String {
        return ExecutionBundle.message("specify.environment.variables.tooltip") + " (" +
                KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)) + ")"
    }

    fun isModifiedSysEnv(v: EnvironmentVariable): Boolean {
        return !v.getNameIsWriteable() && v.getValue() != myParentDefaults.get(v.getName())
    }

    companion object {
         fun convertToVariables(
            map: MutableMap<String, String>,
            readOnly: Boolean
        ):  MutableList<EnvironmentVariable> {
            return ContainerUtil.map<MutableMap.MutableEntry<String, String>, EnvironmentVariable>(
                map.entries,
                Function { entry: MutableMap.MutableEntry<String, String> ->
                    object : EnvironmentVariable(entry.key, entry.value, readOnly) {
                        override fun getNameIsWriteable(): Boolean {
                            return !readOnly
                        }
                    }
                })
        }
    }
}
