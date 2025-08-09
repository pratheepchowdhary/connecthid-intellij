package com.connecthid.intellij.models

import com.connecthid.intellij.utils.PasswordUtil
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
    @Attribute var isInProgress: Boolean=false,
    @Attribute var lastSearchPath: String="",
) {

    val icon by lazy {
        systemInfo.osName.toImageIcon()
    }

    val stmpName by lazy {
        "$username@${host}"
    }

    val rootPath by lazy {
        if (username == "root") "/root" else "/home/${username}"
    }


}


fun Server.setPassword(password: String?) {
    if (password != null) {
        PasswordUtil.storePassword(stmpName, password)
    } else {
        PasswordUtil.deletePassword(stmpName)
    }
}
fun Server.getPassword(): String? {
    return PasswordUtil.getPassword(stmpName)
}