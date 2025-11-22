package com.connecthid.intellij.connection.ssh.system

import com.connecthid.intellij.connection.ssh.SSHJConnection
import net.schmizz.sshj.sftp.SFTPClient
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

private val defaultCommandTimeoutSeconds = 60L

// ------------------------- PUBLIC API -------------------------

fun SSHJConnection.getSystemBasicInformation(): SystemInformation {

    val info = SystemInformation()
    val osName = getOsName()

    info.osName = osName.os

//    fillMemoryAndStorage(info, osName)

//    when (osName) {
//        Os.Linux, Os.MacOS, Os.Windows -> fillOsSpecificInfo(info, osName)
//        Os.Unknown -> {}
//    }

    when (osName) {
        Os.Linux -> collectLinux(info)
        Os.MacOS -> collectMac(info)
        Os.Windows -> collectWindows(info)
        Os.Unknown -> {}
    }
    info.homePath = withSftp {
        getSftpRoot(it)
    }

    println(info)
    return info
}

fun getSftpRoot(sftp: SFTPClient): String {
    return try {
        sftp.canonicalize(".")
    } catch (e: Exception) {
        "."
    }
}

private fun SSHJConnection.fillOsSpecificInfo(info: SystemInformation, os: Os) {

    when (os) {

        // ---------------------------------------------------- LINUX
        Os.Linux -> {
            val osRelease = safeCommand("cat /etc/os-release")
                ?.lines()
                ?.mapNotNull {
                    val p = it.split("=")
                    if (p.size == 2) p[0] to p[1].trim('"') else null
                }?.toMap() ?: emptyMap()

            info.displayName   = osRelease["NAME"]
            info.osVersion     = osRelease["VERSION_ID"]
            info.kernelVersion = safeCommand("uname -r")
            info.architecture  = safeCommand("uname -m")
            info.hostName      = safeCommand("hostname")
            info.defaultShell  = safeCommand("echo \$SHELL")
            info.homePath      = safeCommand("echo \$HOME")

            info.cpuModel = safeCommand(
                "grep 'model name' /proc/cpuinfo | head -1 | cut -d ':' -f 2"
            )?.trim()

            info.cpuCores      = safeCommand("nproc")?.trim()?.toIntOrNull() ?: 0
            info.uptime        = safeCommand("uptime -p")
        }

        // ---------------------------------------------------- MACOS
        Os.MacOS -> {
            info.displayName   = "macOS"
            info.osVersion     = safeCommand("sw_vers -productVersion")
            info.kernelVersion = safeCommand("uname -r")
            info.architecture  = safeCommand("uname -m")
            info.hostName      = safeCommand("hostname")
            info.defaultShell  = safeCommand("echo \$SHELL")
            info.homePath      = safeCommand("echo \$HOME")

            info.cpuModel = safeCommand("sysctl -n machdep.cpu.brand_string")
            info.cpuCores = safeCommand("sysctl -n hw.ncpu")?.trim()?.toIntOrNull() ?: 0

            info.uptime = safeCommand(
                "uptime | sed 's/.*up \\(.*\\), [0-9]* users.*/\\1/'"
            )
        }

        // ---------------------------------------------------- WINDOWS
        Os.Windows -> {
            info.displayName = "Windows"

            info.osVersion = safeCommand(
                "wmic os get Version | findstr /v Version"
            )?.trim()

            info.kernelVersion = info.osVersion // Windows kernel = OS build

            info.architecture = safeCommand(
                "wmic os get OSArchitecture | findstr /v OSArchitecture"
            )?.trim()

            info.hostName = safeCommand("hostname")

            info.defaultShell = "cmd.exe" // You may override to powershell if needed

            info.homePath = safeCommand("echo %HOMEPATH%")?.trim()

            info.cpuModel = safeCommand(
                "wmic cpu get Name | findstr /v Name"
            )?.trim()

            info.cpuCores = safeCommand(
                "wmic cpu get NumberOfCores | findstr /v Number"
            )?.trim()?.toIntOrNull() ?: 0

            info.uptime = safeCommand(
                """
                powershell -command "(get-date) - (gcim Win32_OperatingSystem).LastBootUpTime"
                """.trimIndent()
            )?.trim()
        }

        else -> {}
    }
}


// ------------------------- MEMORY + STORAGE -------------------------

private fun SSHJConnection.fillMemoryAndStorage(info: SystemInformation, os: Os) {

    when (os) {

        Os.Linux -> {
            val totalKb = safeCommand("grep MemTotal /proc/meminfo | awk '{print \$2}'")
                ?.toLongOrNull() ?: 0

            val usedKb = safeCommand("free | grep Mem | awk '{print \$3}'")
                ?.toLongOrNull() ?: 0

            info.totalRam = formatBytes(totalKb * 1024)
            info.usedRam = formatBytes(usedKb * 1024)

            val totalDiskKb = safeCommand("df -k / | tail -1 | awk '{print \$2}'")
                ?.toLongOrNull() ?: 0

            val usedDiskKb = safeCommand("df -k / | tail -1 | awk '{print \$3}'")
                ?.toLongOrNull() ?: 0

            info.totalStorage = formatBytes(totalDiskKb * 1024)
            info.usedStorage = formatBytes(usedDiskKb * 1024)
        }

        Os.MacOS -> {
            val totalBytes = safeCommand("sysctl -n hw.memsize")?.toLongOrNull() ?: 0

            val usedBytes = safeCommand(
                """
                vm_stat | awk '/Pages active/{a=$3} /Pages wired/{w=$4} END{print (a+w)*4096}'
                """.trimIndent()
            )?.toLongOrNull() ?: 0

            info.totalRam = formatBytes(totalBytes)
            info.usedRam = formatBytes(usedBytes)

            val totalDiskKb = safeCommand("df -k / | tail -1 | awk '{print \$2}'")?.toLongOrNull() ?: 0
            val usedDiskKb  = safeCommand("df -k / | tail -1 | awk '{print \$3}'")?.toLongOrNull() ?: 0

            info.totalStorage = formatBytes(totalDiskKb * 1024)
            info.usedStorage = formatBytes(usedDiskKb * 1024)
        }

        Os.Windows -> {
            val totalKb = safeCommand("wmic OS get TotalVisibleMemorySize")
                ?.lines()?.lastOrNull()?.trim()?.toLongOrNull() ?: 0

            val freeKb = safeCommand("wmic OS get FreePhysicalMemory")
                ?.lines()?.lastOrNull()?.trim()?.toLongOrNull() ?: 0

            val usedKb = totalKb - freeKb

            info.totalRam = formatBytes(totalKb * 1024)
            info.usedRam = formatBytes(usedKb * 1024)

            val size = safeCommand("wmic logicaldisk where \"DeviceID='C:'\" get Size")
                ?.lines()?.lastOrNull()?.trim()?.toLongOrNull() ?: 0

            val free = safeCommand("wmic logicaldisk where \"DeviceID='C:'\" get FreeSpace")
                ?.lines()?.lastOrNull()?.trim()?.toLongOrNull() ?: 0

            val used = size - free

            info.totalStorage = formatBytes(size)
            info.usedStorage = formatBytes(used)
        }

        else -> {}
    }
}

/**
 * Runs multiple commands in a single SSH exec() call.
 * All commands are concatenated and separated by markers.
 */
fun SSHJConnection.runBatch(commands: Map<String, String>): Map<String, String?> {
    val marker = "___CHID_MARKER_${System.currentTimeMillis()}___"

    // Build combined command
    val script = buildString {
        commands.forEach { (key, cmd) ->
            appendLine("echo $marker$key")
            appendLine(cmd)
        }
        appendLine("echo ${marker}END")
    }

    val (code, output) = command(script)
    if (code != 0) return emptyMap()

    val result = mutableMapOf<String, String?>()
    var currentKey: String? = null
    val sb = StringBuilder()

    output.lines().forEach { line ->
        if (line.startsWith(marker)) {
            // Save previous block
            currentKey?.let { result[it] = sb.toString().trim() }
            sb.clear()
            currentKey = line.removePrefix(marker)
            return@forEach
        }
        sb.appendLine(line)
    }

    return result
}

private fun SSHJConnection.collectLinux(info: SystemInformation) {

    val results = runBatch(
        mapOf(
            "os_release"   to "cat /etc/os-release",
            "kernel"       to "uname -r",
            "arch"         to "uname -m",
            "host"         to "hostname",
            "shell"        to "echo \$SHELL",
            "home"         to "echo \$HOME",

            // CPU
            "cpu_model"    to "grep 'model name' /proc/cpuinfo | head -1 | cut -d ':' -f 2",
            "cpu_cores"    to "nproc",

            // RAM
            "meminfo"      to "cat /proc/meminfo",
            "used_ram"     to "free -m | awk '/Mem:/ { print \$3 }'",

            // Storage
            "disk_total"   to "df -h / | awk 'NR==2{print \$2}'",
            "disk_used"    to "df -h / | awk 'NR==2{print \$3}'",

            // Uptime
            "uptime"       to "uptime -p"
        )
    )

    val osRelease = results["os_release"]?.lines()?.associate {
        val p = it.split("=")
        if (p.size == 2) p[0] to p[1].trim('"') else "" to ""
    } ?: emptyMap()

    info.displayName   = osRelease["NAME"]
    info.osVersion     = osRelease["VERSION_ID"]
    info.kernelVersion = results["kernel"]
    info.architecture  = results["arch"]
    info.hostName      = results["host"]
    info.defaultShell  = results["shell"]
    info.homePath      = results["home"]

    info.cpuModel      = results["cpu_model"]?.trim()
    info.cpuCores      = results["cpu_cores"]?.trim()?.toIntOrNull() ?: 0

    val meminfo        = results["meminfo"] ?: ""
    info.totalRam      = Regex("MemTotal:\\s+(\\d+) kB")
        .find(meminfo)?.groupValues?.get(1)
        ?.let { "${it.toLong() / 1024} MB" }

    info.usedRam       = results["used_ram"]
    info.totalStorage  = results["disk_total"]
    info.usedStorage   = results["disk_used"]
    info.uptime        = results["uptime"]
}
private fun SSHJConnection.collectMac(info: SystemInformation) {

    val results = runBatch(
        mapOf(
            "name"       to "sw_vers -productName",
            "version"    to "sw_vers -productVersion",
            "kernel"     to "uname -r",
            "arch"       to "uname -m",
            "host"       to "hostname",
            "shell"      to "echo \$SHELL",
            "home"       to "echo \$HOME",

            "cpu_model"  to "sysctl -n machdep.cpu.brand_string",
            "cpu_cores"  to "sysctl -n hw.ncpu",

            "ram_size"   to "sysctl -n hw.memsize",

            "active_pages" to "vm_stat | grep 'Pages active' | awk '{print \$3}'",

            "disk_total" to "df -h / | awk 'NR==2{print \$2}'",
            "disk_used"  to "df -h / | awk 'NR==2{print \$3}'",

            "uptime"     to "uptime | sed 's/.*up \\(.*\\), [0-9]* users.*/\\1/'"
        )
    )

    info.displayName   = results["name"]
    info.osVersion     = results["version"]
    info.kernelVersion = results["kernel"]
    info.architecture  = results["arch"]
    info.hostName      = results["host"]
    info.defaultShell  = results["shell"]
    info.homePath      = results["home"]

    info.cpuModel      = results["cpu_model"]
    info.cpuCores      = results["cpu_cores"]?.toIntOrNull() ?: 0

    info.totalRam = results["ram_size"]
        ?.toLongOrNull()
        ?.let { "${it / (1024 * 1024)} MB" }

    info.usedRam = results["active_pages"]
        ?.replace(".", "")
        ?.toLongOrNull()
        ?.let { "${(it * 4096) / (1024 * 1024)} MB" }

    info.totalStorage = results["disk_total"]
    info.usedStorage  = results["disk_used"]
    info.uptime       = results["uptime"]
}
private fun SSHJConnection.collectWindows(info: SystemInformation) {

    val results = runBatch(
        mapOf(
            "caption" to "wmic os get Caption | findstr /v Caption",
            "version" to "wmic os get Version | findstr /v Version",
            "arch"    to "wmic os get OSArchitecture | findstr /v OSArchitecture",
            "host"    to "hostname",
            "home"    to "echo %USERPROFILE%",

            "cpu_model" to "wmic cpu get Name | findstr /v Name",
            "cpu_cores" to "wmic cpu get NumberOfCores | findstr /v Number",

            "ram_total" to "wmic computersystem get TotalPhysicalMemory | findstr /v Total",

            "ram_used"  to """
                powershell -command "(Get-CimInstance Win32_OperatingSystem).TotalVisibleMemorySize - (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory"
            """.trimIndent(),

            "uptime" to """
                powershell -command "(get-date) - (gcim Win32_OperatingSystem).LastBootUpTime"
            """.trimIndent()
        )
    )

    info.displayName = results["caption"]
    info.osVersion   = results["version"]
    info.kernelVersion = info.osVersion
    info.architecture = results["arch"]
    info.hostName     = results["host"]
    info.defaultShell = "cmd.exe"
    info.homePath     = results["home"]

    info.cpuModel = results["cpu_model"]
    info.cpuCores = results["cpu_cores"]?.trim()?.toIntOrNull() ?: 0

    info.totalRam = results["ram_total"]
        ?.trim()?.toLongOrNull()
        ?.let { "${it / (1024 * 1024)} MB" }

    info.usedRam = results["ram_used"]
        ?.trim()?.toLongOrNull()
        ?.let { "${it / 1024} MB" }

    info.uptime = results["uptime"]
}



// ------------------------- OS NAME -------------------------

fun SSHJConnection.getOsName(): Os {
    val result = safeCommand(Commands.osName)?.lowercase() ?: return Os.Unknown

    return when {
        "linux" in result -> Os.Linux
        "darwin" in result -> Os.MacOS
        "windows" in result -> Os.Windows
        else -> Os.Unknown
    }
}
fun String.getOs(): Os {
    return try {
        Os.valueOf(this)
    } catch (e: IllegalArgumentException) {
        Os.Unknown
    }
}

// ------------------------- SAFE COMMAND WRAPPERS -------------------------

/** Returns stdout or null on any error */
fun SSHJConnection.safeCommand(cmd: String): String? {
    return try {
        val (exit, output) = command(cmd)
        if (exit == 0) output.trim().ifBlank { null } else null
    } catch (_: Exception) {
        null
    }
}

/** Raw command with proper SSHJ handling */
fun SSHJConnection.command(cmd: String, timeoutSeconds: Long = defaultCommandTimeoutSeconds): Pair<Int, String> {

    return withExc {
        val session = it.exec(cmd)
        try {
            val stdout = readStream(session.inputStream)
            val stderr = readStream(session.errorStream)

            session.join(timeoutSeconds, TimeUnit.SECONDS)
            val exit = session.exitStatus

            // Only throw when real error, not warnings
            if (exit != 0 && stderr.isNotBlank())
                throw IOException("Command failed: $stderr")

            Pair(exit, stdout)
        } finally {
            runCatching { session.close() }
        }
    }
}

// ------------------------- HELPERS -------------------------

private fun readStream(inputStream: InputStream?): String =
    inputStream?.bufferedReader()?.use { it.readText() } ?: ""

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val i = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.2f %s".format(bytes / Math.pow(1024.0, i.toDouble()), units[i])
}
