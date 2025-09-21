package com.connecthid.intellij.models

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

@Tag("workspace")
data class Workspace( @Attribute val server: String="", @Attribute val path: String="", @Attribute val folderName: String=""){

}

