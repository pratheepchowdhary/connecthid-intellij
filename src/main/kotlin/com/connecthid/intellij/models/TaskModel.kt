package com.connecthid.intellij.models

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

@Tag("script")
data class TaskModel(
    @Attribute var scriptId: String = "",
    @Attribute var scriptName: String = "",
    @Attribute var scriptType: Int = 0,
    @Attribute var server: String = "",
    @Attribute var workingDir: String = "",
    @Attribute var scriptFile: String = "",
    @Attribute var scriptText: String = "",
    @Attribute var isScriptFile: Boolean = false,
    @Attribute var scriptOptions: String = "",
    @Attribute var interpreterPath: String = "",
    @Attribute var interpreterOptions: String = "",
    @Attribute var executeInTerminal: Boolean = false,
    @Attribute var executeInRunConfigurations: Boolean = false,
    @Attribute var envData: String = "",
    @Attribute var runAfter: String = "",
    @Attribute var passParentEnv: Boolean = false


)
