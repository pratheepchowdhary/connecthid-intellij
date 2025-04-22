package com.connecthid.intellij.models

import com.connecthid.intellij.utils.toImageIcon
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

@Tag("server")
data class Server(
    @Attribute var host: String="",
    @Attribute var username: String="",
    @Attribute var port: Int = 22,
    @Attribute(converter = AuthenticationMethodConverter::class)
    var authMethod: AuthenticationMethod = AuthenticationMethod.NONE,
    var privateKeyPath: String? = null,
    @Attribute(converter = SystemInfoConverter::class)
    var systemInfo: SystemInfo = SystemInfo(),
    @Attribute var isInProgress: Boolean=false
){

    val icon by lazy {
        systemInfo.osName.toImageIcon()
    }
    val password by lazy {
        "aA1pradeep"
    }
    val stmpName by lazy {
        "STMP: ${host}"
    }

}