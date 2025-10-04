package com.connecthid.intellij.models

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

@Tag("script")
data class Script(
    @Attribute val scriptId: String = "",
    @Attribute val scriptName: String = "",
    @Attribute val scriptType: Int = 0,
    @Attribute val server: String = "",
    @Attribute val workingDir: String = "",
    @Attribute val scriptFile: String = "",
    @Attribute val scriptText: String = "",
    @Attribute val isScriptFile: Boolean = false,
    @Attribute val scriptOptions: String = "",
    @Attribute val interpreterPath: String = "",
    @Attribute val interpreterOptions: String = "",
    @Attribute val executeInTerminal: Boolean = false,
    @Attribute val executeInRunConfigurations: Boolean = false
)
