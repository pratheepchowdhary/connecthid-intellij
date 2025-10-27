/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.connecthid.intellij.ui.commons.ssh

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.EnvFilesOptions
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.UserActivityProviderComponent
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.VerticalComponentGap
import javax.swing.event.ChangeListener

class EnvironmentVariablesComponent(val project: Project, val systemVariables:()-> MutableMap<String, String>) : LabeledComponent<TextFieldWithBrowseButton>(), UserActivityProviderComponent {
    val myEnvVars: EnvironmentVariablesTextFieldWithBrowseButton

    init {
        myEnvVars = createBrowseComponent()
        setComponent(myEnvVars)
        setText(ExecutionBundle.message("environment.variables.component.title"))
        putClientProperty(DslComponentProperty.INTERACTIVE_COMPONENT, myEnvVars.getChildComponent())
        putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.Companion.BOTH)
    }

    protected fun createBrowseComponent(): EnvironmentVariablesTextFieldWithBrowseButton {
        return EnvironmentVariablesTextFieldWithBrowseButton(project,systemVariables)
    }

    var envs: MutableMap<String?, String?>
        get() = myEnvVars.envs
        set(envs) {
            myEnvVars.envs = envs
        }

    var isPassParentEnvs: Boolean
        get() = myEnvVars.isPassParentEnvs
        set(passParentEnvs) {
            myEnvVars.isPassParentEnvs = passParentEnvs
        }

    var envFilePaths: MutableList<String>
        get() = myEnvVars.envFilePaths
        set(envFilePaths) {
            myEnvVars.envFilePaths = envFilePaths
        }

    var envData: EnvironmentVariablesData
        get() = myEnvVars.data
        set(envData) {
            myEnvVars.data = envData
        }

    fun reset(s: CommonProgramRunConfigurationParameters) {
        this.envs = s.getEnvs()
        this.isPassParentEnvs = s.isPassParentEnvs()
        if (s is EnvFilesOptions) {
            myEnvVars.envFilePaths = (s as EnvFilesOptions).envFilePaths.toMutableList()
        }
    }

    fun apply(s: CommonProgramRunConfigurationParameters) {
        s.setEnvs(this.envs)
        s.setPassParentEnvs(this.isPassParentEnvs)
        if (s is EnvFilesOptions) {
            (s as EnvFilesOptions).envFilePaths = myEnvVars.envFilePaths
        }
    }

    override fun addChangeListener(changeListener: ChangeListener) {
        myEnvVars.addChangeListener(changeListener)
    }

    override fun removeChangeListener(changeListener: ChangeListener) {
        myEnvVars.removeChangeListener(changeListener)
    }
}
