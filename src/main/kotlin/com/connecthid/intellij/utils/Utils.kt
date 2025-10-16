package com.connecthid.intellij.utils

import com.connecthid.intellij.models.SftpUrl
import org.jdom.Element

object Utils {
    fun parseSftpUrl(url: String): SftpUrl {
        val uri = java.net.URI(url)

        // Extract username (before @ in userInfo)
        val userInfo = uri.userInfo      // "username" in your format
        val username = userInfo?.takeIf { it.isNotEmpty() }

        // Host and port
        val host = uri.host ?: throw IllegalArgumentException("Host missing in URL")
        val port = if (uri.port == -1) 22 else uri.port  // default SFTP port 22

        // Path
        val path = uri.path ?: "/"

        return SftpUrl(username, host, port, path)
    }


    fun mapStringToElement(data: String,passParentEnv: Boolean=true): Element {
        val parentElement = Element("parent")
        val envsElement = Element("envs")
        envsElement.setAttribute("pass-parent-envs", passParentEnv.toString())
        parentElement.addContent(envsElement)

        data.split(";").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                val envElement = Element("env")
                envElement.setAttribute("name", parts[0])
                envElement.setAttribute("value", parts[1])
                envsElement.addContent(envElement)
            }
        }
        return parentElement
    }
    fun mapStringToEnvMap(data: String): MutableMap<String, String>  {
        val parentElement :MutableMap<String, String> = mutableMapOf()

        data.split(";").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                parentElement.put(parts[0],parts[1])
            }
        }
        return parentElement
    }

}