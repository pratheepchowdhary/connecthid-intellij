package com.connecthid.intellij.connection.ssh

import com.connecthid.intellij.connection.sftp.SftpFile
import com.connecthid.intellij.connection.ssh.system.SystemInformation
import com.connecthid.intellij.connection.ssh.system.getSystemBasicInformation
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.SftpFileOccurrence
import com.connecthid.intellij.models.SftpMatchInfo
import com.connecthid.sshjpool.SSHConnection
import com.connecthid.sshjpool.SSHConnectionPool
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.scp.SCPFileTransfer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * SSHConnection implemented with SSHJ.
 *
 * Kept utility functions from original class (searchFiles, searchTextInFiles, listFolderPaths, etc.)
 * Removed channel pooling & locks — SSHJ client/sessions are used per-operation (lightweight).
 */
class SSHJConnection(
    private val host: String,
    private val username: String,
    private val password: String? = null,
    private val port: Int = 22,
    private val privateKeyPath: String? = null
) {
    private val defaultCommandTimeoutSeconds = 60L
    private var maxPoolSize: Int = 5
    val sshPool : SSHConnectionPool

    init {
        sshPool = SSHConnectionPool(host, username, password!!, maxPoolSize, maxPoolSize)
    }

    /**
     * Ensure connected; idempotent.
     */
    @Synchronized
    fun connect() {
        val client = sshPool.borrowAvailableConnection()
        if(!client.isAlive()){
           throw Exception("Connection Failed")
        }
    }


    fun isConnected(): Boolean = sshPool.isConnected()

    fun getSystemInfo():SystemInformation{
        return getSystemBasicInformation()
    }

    /**
     * Execute a single command non-interactively and return stdout.
     * If stderr contains content, it will be included in the exception message.
     */
    @Throws(IOException::class)
    fun execute(command: String, timeoutSeconds: Long = defaultCommandTimeoutSeconds): String {
        return sshPool.withExc {
            val cmd = it.exec(command)
            try {
                val stdout = readStream(cmd.inputStream)
                val stderr = readStream(cmd.errorStream)
                // Wait for command to finish (bounded)
                cmd.join(timeoutSeconds, TimeUnit.SECONDS)
                val exitStatus = cmd.exitStatus
                if (stderr.isNotBlank()) {
                    throw IOException("Command failed (exit=$exitStatus): $stderr")
                }
                return@withExc stdout
            } catch (ex: Exception){
                ex.printStackTrace()
            }finally {
                try { cmd.close() } catch (_: Exception) {}
            }
            return@withExc ""
        }
    }

    /**
     * Open SFTP client, execute block, then close SFTP client.
     * Use for put/get and other SFTP operations.
     */
    @Throws(IOException::class)
     fun <T> withSftp(action: (SFTPClient) -> T): T {
        return sshPool.withSftp(action)
     }

    @Throws(IOException::class)
    fun <T> withExc(action: (Session) -> T): T {
        return sshPool.withExc (action)
    }

    @Throws(IOException::class)
    fun <T> withScp(action: (SCPFileTransfer) -> T): T {
        return sshPool.withScp (action)
    }

    fun getSftpClient():Pair<SSHConnection, SFTPClient>  {
        return sshPool.borrowSftpClient()
    }


    /**
     * Upload local -> remote
     */
    @Throws(IOException::class)
    fun uploadFile(localPath: String, remotePath: String) {
        withSftp { sftp ->
            sftp.put(localPath, remotePath)
        }
    }

    /**
     * Download remote -> local
     */
    @Throws(IOException::class)
    fun downloadFile(remotePath: String, localPath: String) {
        withSftp { sftp ->
            // Ensure local parent exists
            val localFile = Paths.get(localPath)
            localFile.parent?.let { Files.createDirectories(it) }
            sftp.get(remotePath, localPath)
        }
    }

    private fun readStream(inputStream: InputStream?): String {
        if (inputStream == null) return ""
        return inputStream.bufferedReader().use { it.readText() }
    }

    /**
     * Install a local public key into remote ~/.ssh/authorized_keys (appends if already exists).
     */
    @Throws(IOException::class)
    fun installPublicKey(publicKeyPath: String) {
        val publicKey = File(publicKeyPath).readText(Charsets.UTF_8).trim()
        // Create .ssh and set permissions, then append public key
        val cmd = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo '${escapeForSingleQuotes(publicKey)}' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        execute(cmd)
    }

    /**
     * Generate RSA key pair and write files (private PEM, public OpenSSH format).
     */
    @Throws(IOException::class)
    fun generateKeyPair(privateKeyPath: String, publicKeyPath: String) {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.generateKeyPair()

        val privateKey = keyPair.private as RSAPrivateKey
        val publicKey = keyPair.public as RSAPublicKey

        // Private key PEM (PKCS#8)
        val privatePem = buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            appendLine(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKey.encoded))
            appendLine("-----END PRIVATE KEY-----")
        }
        Files.write(Paths.get(privateKeyPath), privatePem.toByteArray())

        // Public key in OpenSSH format
        val sshPubKey = encodePublicKey(publicKey, username)
        Files.write(Paths.get(publicKeyPath), sshPubKey.toByteArray())
    }

    /**
     * Encode RSA public key to OpenSSH format ("ssh-rsa ... comment")
     */
    private fun encodePublicKey(pubKey: RSAPublicKey, comment: String): String {
        fun intToBytes(i: Int): ByteArray {
            return byteArrayOf(
                ((i shr 24) and 0xff).toByte(),
                ((i shr 16) and 0xff).toByte(),
                ((i shr 8) and 0xff).toByte(),
                (i and 0xff).toByte()
            )
        }

        fun writeString(s: String, buf: MutableList<Byte>) {
            val b = s.toByteArray()
            buf.addAll(intToBytes(b.size).toList())
            buf.addAll(b.toList())
        }

        fun writeBytes(b: ByteArray, buf: MutableList<Byte>) {
            buf.addAll(intToBytes(b.size).toList())
            buf.addAll(b.toList())
        }

        val buf = mutableListOf<Byte>()
        writeString("ssh-rsa", buf)

        val pubExp = pubKey.publicExponent.toByteArray()
        val modulus = pubKey.modulus.toByteArray()

        writeBytes(pubExp, buf)
        writeBytes(modulus, buf)

        val keyBytes = buf.toByteArray()
        val base64Encoded = Base64.getEncoder().encodeToString(keyBytes)
        return "ssh-rsa $base64Encoded $comment"
    }

    private fun escapeForSingleQuotes(s: String): String {
        // replace single quote ' with '"'"' which is safe to include inside single-quoted shell string
        return s.replace("'", "'\"'\"'")
    }

    /**
     * Attempt to detect MaxSessions via remote sshd -T or sshd_config fallback; returns an Int or default 5.
     */
    fun getMaxSessionsValue(): Int {
        return try {
            val output = execute("sshd -T 2>/dev/null | grep -i maxsessions || grep -i MaxSessions /etc/ssh/sshd_config")
            // Try to extract first numeric token from a matching line
            output.lines().firstOrNull { it.contains("maxsessions", ignoreCase = true) }?.let { line ->
                Regex("\\d+").find(line)?.value?.toIntOrNull()
            } ?: 5
        } catch (e: Exception) {
            5
        }
    }

    /**
     * Search files by name (uses `find` remote command).
     * Returns list of SftpFile (you provide model).
     */
    fun searchFiles(
        server: Server,
        pattern: String,
        path: String = server.rootPath,
        word: Boolean = false,
        case: Boolean = false,
        regexp: Boolean = false
    ): List<SftpFile> {
        val results = mutableListOf<SftpFile>()
        if (pattern.length < 2) return results

        try {
            var findPattern = pattern
            var findFlag = "-name"
            if (regexp) {
                findFlag = "-regex"
                if (!findPattern.startsWith(".*")) findPattern = ".*$findPattern"
            } else if (word) {
                findFlag = "-name"
            } else {
                findFlag = if (case) "-name" else "-iname"
                findPattern = if (pattern.contains("*")) pattern else "*$pattern*"
            }
            val excludeHidden = "! -path '*/.*'"
            val findCommand = "find '${escapeForSingleQuotes(path)}' -type f $excludeHidden $findFlag '${escapeForSingleQuotes(findPattern)}' 2>/dev/null"
            val found = execute(findCommand, 30)
            val lines = found.lines().filter { it.isNotBlank() }
            for (line in lines) {
                results.add(SftpFile(line, server))
            }
        } catch (e: Exception) {
            // keep behavior similar to previous: print stacktrace but don't crash the caller
            e.printStackTrace()
        }
        return results
    }

    /**
     * List immediate subdirectories of given path (find -maxdepth 1 -type d).
     */
    fun listFolderPaths(server: Server, path: String = server.rootPath): List<String> {
        val results = mutableListOf<String>()
        try {
            val findCommand = "find '${escapeForSingleQuotes(path)}' -mindepth 1 -maxdepth 1 -type d 2>/dev/null"
            val found = execute(findCommand, 20)
            results.addAll(found.lines().filter { it.isNotBlank() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    /**
     * Search text inside files (uses grep -b -n -r etc.). Returns structured occurrences.
     */
    fun searchTextInFiles(
        server: Server,
        pattern: String,
        path: String = server.rootPath,
        word: Boolean = false,
        case: Boolean = false,
        regexp: Boolean = false
    ): List<SftpFileOccurrence> {
        val results = mutableListOf<SftpFileOccurrence>()
        if (pattern.isEmpty()) return results

        try {
            val options = mutableListOf("-n", "-b", "-r", "-o") // line no, byte offset, recursive, only matching
            if (word) options.add("-w")
            if (!case) options.add("-i")
            if (!regexp) options.add("-F")
            val escapedPattern = escapeForSingleQuotes(pattern)
            val grepCommand = "grep ${options.joinToString(" ")} --exclude-dir='.*' --exclude='.*' '${escapedPattern}' '${escapeForSingleQuotes(path)}' 2>/dev/null"
            val out = execute(grepCommand, 60)
            val foundLines = out.lines().filter { it.isNotBlank() }

            val fileOccurrences = mutableMapOf<String, MutableList<SftpMatchInfo>>()

            for (occ in foundLines) {
                // Expect: filepath:line_number:byte_offset:matching_text
                val colonIndices = findFirstNColonIndices(occ, 3)
                if (colonIndices.size >= 3) {
                    val filePath = occ.substring(0, colonIndices[0])
                    val lineNumber = occ.substring(colonIndices[0] + 1, colonIndices[1]).toIntOrNull() ?: 1
                    val byteOffset = occ.substring(colonIndices[1] + 1, colonIndices[2]).toIntOrNull() ?: 0
                    val lineContent = occ.substring(colonIndices[2] + 1)
                    val matchOffset = byteOffset
                    val matchEndOffset = matchOffset + lineContent.length
                    val fileMatches = fileOccurrences.getOrPut(filePath) { mutableListOf() }
                    fileMatches.add(SftpMatchInfo(lineNumber, matchOffset, matchEndOffset, lineContent))
                }
            }

            for ((filePath, matches) in fileOccurrences) {
                val file = SftpFile(filePath, server)
                results.add(SftpFileOccurrence(file, matches))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun findFirstNColonIndices(str: String, n: Int): List<Int> {
        val indices = mutableListOf<Int>()
        var pos = -1
        repeat(n) {
            pos = str.indexOf(':', pos + 1)
            if (pos != -1) {
                indices.add(pos)
            } else {
                return indices
            }
        }
        return indices
    }

    /**
     * Returns remote environment variables as a Map (tries 'env' then 'cmd.exe /c set').
     */
    @Throws(IOException::class)
    fun getEnvironmentVariables(): Map<String, String> {
        val output = try {
            execute("env", 10)
        } catch (e: IOException) {
            try {
                execute("cmd.exe /c set", 10)
            } catch (fallback: IOException) {
                throw IOException("Failed to fetch environment variables: ${e.message}. Tried 'env' and 'set'.", fallback)
            }
        }
        return output.lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || !trimmed.contains("=")) return@mapNotNull null
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2 && parts[0].isNotEmpty()) parts[0].trim() to parts[1].trim() else null
            }
            .toMap()
    }

    /**
     * Cleanly close connection
     */
    fun close() {
        sshPool.shutdown()
    }
}
