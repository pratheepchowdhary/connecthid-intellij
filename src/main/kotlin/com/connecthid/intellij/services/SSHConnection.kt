package com.connecthid.intellij.services

import com.connecthid.intellij.connection.vfs.SftpFileSystem
import com.jcraft.jsch.*
import com.jetbrains.rd.util.printlnError
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.Condition


class SSHConnection(
    private val host: String,
    private val username: String,
    private val password: String? = null,
    private val port: Int = 22,
    private val privateKeyPath: String? = null
) {
    private val jsch = JSch()
    private var session: Session? = null
    // SFTP Channel Pooling
    private val channelPool = mutableListOf<ChannelSftp>()
    // Use a single lock for both pools
    private val channelPoolLock = ReentrantLock()
    private val channelAvailable: Condition = channelPoolLock.newCondition()
    var maxChannels = 5
    private var totalChannels = 0 // Tracks all open channels (in pool + checked out)
    var fileSystem : SftpFileSystem? = null

    // Exec Channel Pooling
    private val execChannelPool = mutableListOf<ChannelExec>()

    init {
        JSch.setConfig("StrictHostKeyChecking", "no")
    }



    fun connect() {
        try {
            session = jsch.getSession(username, host, port)
            
            if (privateKeyPath != null) {
                // Use private key authentication
                jsch.addIdentity(privateKeyPath)
            } else if (password != null) {
                // Use password authentication
                session!!.setPassword(password)
            } else {
                throw IOException("No authentication method provided")
            }
            
            session!!.connect()
            maxChannels = getMaxSessionsValue(session!!)
        } catch (e: JSchException) {
            throw IOException("Failed to connect to $host: ${e.message}", e)
        }
    }


    fun disconnect() {
        fileSystem = null
        session?.disconnect()
    }

    fun isConnected(): Boolean {
        if (session == null || !session!!.isConnected) {
            fileSystem = null
            return false
        }
        return session?.isConnected == true
    }

    fun getSession(): Session? {
        return session
    }

    fun execute(command: String): String {
        if (!isConnected()) {
            connect()
        }
        val channel = getExecChannelFromPool()
        try {
            channel?.setCommand(command)
            channel?.connect()
            val inputStream = channel?.inputStream
            val errorStream = (channel as ChannelExec).errStream

            val output = readStream(inputStream)
            val error = readStream(errorStream)

            if (error.isNotEmpty()) {
                throw IOException("Command execution failed: $error")
            }
            return output
        } catch (e: JSchException) {
            throw IOException("Failed to execute command: ${e.message}", e)
        } finally {
            releaseExecChannelToPool(channel)
        }
    }



    fun getChannelFromPool(): ChannelSftp? {
        channelPoolLock.lock()
        try {
            cleanupChannels()
            printlnError("[getChannelFromPool] Enter: totalChannels=$totalChannels, poolSize=${channelPool.size}")
            if (channelPool.isNotEmpty()) {
                val ch = channelPool.removeAt(0)
                printlnError("[getChannelFromPool] Reusing channel from pool. totalChannels=$totalChannels, poolSize=${channelPool.size}")
                return ch
            }
            if (totalChannels < maxChannels) {
                if (!isConnected()) connect()
                val channel = session?.openChannel("sftp") as? ChannelSftp
                channel?.connect()
                if (channel != null) {
                    totalChannels++
                    printlnError("[getChannelFromPool] Created new SFTP channel. totalChannels=$totalChannels")
                }
                return channel
            }
            var waited = 0L
            val maxWait = 10000L // 10 seconds max wait
            while (channelPool.isEmpty() && waited < maxWait) {
                channelAvailable.awaitNanos(500_000_000) // 0.5s
                printlnError("[getChannelFromPool] Waiting for available SFTP channel... totalChannels=$totalChannels")
                waited += 500
            }
            // Re-check if we can create a new channel after waiting
            if (channelPool.isEmpty() && totalChannels < maxChannels) {
                if (!isConnected()) connect()
                val channel = session?.openChannel("sftp") as? ChannelSftp
                channel?.connect()
                if (channel != null) {
                    totalChannels++
                    printlnError("[getChannelFromPool] Created new SFTP channel after wait. totalChannels=$totalChannels")
                }
                return channel
            }
            printlnError("[getChannelFromPool] Returning null. totalChannels=$totalChannels, poolSize=${channelPool.size}")
            return if (channelPool.isNotEmpty()) channelPool.removeAt(0) else null
        } finally {
            channelPoolLock.unlock()
        }
    }

    fun releaseChannelToPool(channel: ChannelSftp?) {
        if (channel == null) return
        channelPoolLock.lock()
        try {
            if (channel.isConnected && totalChannels <= maxChannels) {
                channelPool.add(channel)
                channelAvailable.signal()
                printlnError("[releaseChannelToPool] Channel returned to pool. totalChannels=$totalChannels, poolSize=${channelPool.size}")
            } else {
                try { channel.disconnect() } catch (_: Exception) {}
                totalChannels--
                printlnError("[releaseChannelToPool] Channel disconnected and totalChannels decremented. totalChannels=$totalChannels")
            }
        } finally {
            cleanupChannels()
            channelPoolLock.unlock()
        }
    }

    fun getExecChannelFromPool(): ChannelExec? {
        channelPoolLock.lock()
        try {
            cleanupChannels()
            printlnError("[getExecChannelFromPool] Enter: totalChannels=$totalChannels, poolSize=${execChannelPool.size}")
            if (execChannelPool.isNotEmpty()) {
                val ch = execChannelPool.removeAt(0)
                printlnError("[getExecChannelFromPool] Reusing exec channel from pool. totalChannels=$totalChannels, poolSize=${execChannelPool.size}")
                return ch
            }
            if (totalChannels < maxChannels) {
                if (!isConnected()) connect()
                val channel = session?.openChannel("exec") as? ChannelExec
                if (channel != null) {
                    totalChannels++
                    printlnError("[getExecChannelFromPool] Created new exec channel. totalChannels=$totalChannels")
                }
                return channel
            }
            var waited = 0L
            val maxWait = 10000L // 10 seconds max wait
            while (execChannelPool.isEmpty() && waited < maxWait) {
                channelAvailable.awaitNanos(500_000_000) // 0.5s
                printlnError("[getExecChannelFromPool] Waiting for exec channel... totalChannels=$totalChannels")
                waited += 500
            }
            // Re-check if we can create a new channel after waiting
            if (execChannelPool.isEmpty() && totalChannels < maxChannels) {
                if (!isConnected()) connect()
                val channel = session?.openChannel("exec") as? ChannelExec
                if (channel != null) {
                    totalChannels++
                    printlnError("[getExecChannelFromPool] Created new exec channel after wait. totalChannels=$totalChannels")
                }
                return channel
            }
            printlnError("[getExecChannelFromPool] Returning null. totalChannels=$totalChannels, poolSize=${execChannelPool.size}")
            return if (execChannelPool.isNotEmpty()) execChannelPool.removeAt(0) else null
        } finally {
            channelPoolLock.unlock()
        }
    }

    fun releaseExecChannelToPool(channel: ChannelExec?) {
        if (channel == null) return
        channelPoolLock.lock()
        try {
            if (channel.isConnected && totalChannels <= maxChannels) {
                execChannelPool.add(channel)
                channelAvailable.signal()
                printlnError("[releaseExecChannelToPool] Exec channel returned to pool. totalChannels=$totalChannels, poolSize=${execChannelPool.size}")
            } else {
                try { channel.disconnect() } catch (_: Exception) {}
                totalChannels--
                printlnError("[releaseExecChannelToPool] Exec channel disconnected and totalChannels decremented. totalChannels=$totalChannels")
            }
        } finally {
            cleanupChannels()
            channelPoolLock.unlock()
        }
    }

    private fun cleanupChannels() {
        val before = channelPool.size
        channelPool.removeIf { !it.isConnected }
        val after = channelPool.size
        if (before - after > 0) {
            totalChannels -= (before - after)
            printlnError("[cleanupChannels] Removed ${before - after} SFTP channels. totalChannels=$totalChannels")
        }
        val beforeExec = execChannelPool.size
        execChannelPool.removeIf { !it.isConnected }
        val afterExec = execChannelPool.size
        if (beforeExec - afterExec > 0) {
            totalChannels -= (beforeExec - afterExec)
            printlnError("[cleanupChannels] Removed ${beforeExec - afterExec} exec channels. totalChannels=$totalChannels")
        }
        printlnError("[cleanupChannels] Available SFTP channels: ${channelPool.size}, Exec channels: ${execChannelPool.size}, Total channels: $totalChannels")
    }

    fun uploadFile(localPath: String, remotePath: String) {
        val sftpChannel = getChannelFromPool()
        try {
            sftpChannel?.put(localPath, remotePath)
        } catch (e: SftpException) {
            throw IOException("Failed to upload file: ${e.message}", e)
        } finally {
            releaseChannelToPool(sftpChannel)
        }
    }

    fun downloadFile(remotePath: String, localPath: String) {
        val sftpChannel = getChannelFromPool()
        try {
            sftpChannel?.get(remotePath, localPath)
        } catch (e: SftpException) {
            throw IOException("Failed to download file: ${e.message}", e)
        } finally {
            releaseChannelToPool(sftpChannel)
        }
    }

    private fun readStream(inputStream: InputStream?): String {
        if (inputStream == null) return ""
        val reader = inputStream.bufferedReader()
        return reader.use { it.readText() }
    }

    /**
     * Install public key from local file to remote ~/.ssh/authorized_keys.
     *
     * @param publicKeyPath local path to public key file
     */
    fun installPublicKey(publicKeyPath: String) {
        val publicKey = File(publicKeyPath).readText(Charsets.UTF_8).trim()
        //create folder if not exits and add permission
        execute("mkdir -p ~/.ssh && chmod 700 ~/.ssh")
        val appendCmd = "echo '$publicKey' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        execute(appendCmd)
        println("Public key installed successfully")
    }

    /**
     * Generate RSA key pair and save to files.
     *
     * @param privateKeyPath where to save the private key (PEM format)
     * @param publicKeyPath where to save the public key (OpenSSH format)
     */
    fun generateKeyPair(privateKeyPath: String, publicKeyPath: String) {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.generateKeyPair()

        val privateKey = keyPair.private as RSAPrivateKey
        val publicKey = keyPair.public as RSAPublicKey

        // Save private key in PEM format (PKCS#8, unencrypted)
        val privateKeyPem = buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            appendLine(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKey.encoded))
            appendLine("-----END PRIVATE KEY-----")
        }
        Files.write(Paths.get(privateKeyPath), privateKeyPem.toByteArray())

        // Save public key in OpenSSH format
        val sshPubKey = encodePublicKey(publicKey, username)
        Files.write(Paths.get(publicKeyPath), sshPubKey.toByteArray())

        println("Key pair generated:")
        println("Private key: $privateKeyPath")
        println("Public key: $publicKeyPath")
    }

    /**
     * Helper to encode RSA public key in OpenSSH format:
     * "ssh-rsa <base64-encoded key> username"
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
        // SSH public key format encoding (simplified)
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

    fun getMaxSessionsValue(session: Session): Int {
        return try {
            val channel = session.openChannel("exec") as ChannelExec

            // Use sshd -T if available, else fallback to reading sshd_config
            val command = "sshd -T 2>/dev/null | grep -i maxsessions || grep -i MaxSessions /etc/ssh/sshd_config"
            channel.setCommand(command)
            channel.setInputStream(null)
            val outputStream = ByteArrayOutputStream()
            channel.outputStream = outputStream
            channel.connect()
            // Wait for command to complete
            while (!channel.isClosed) {
                Thread.sleep(100)
            }
            channel.disconnect()
            val output = outputStream.toString().trim()
            return output.lines().firstOrNull { it.contains("maxsessions", ignoreCase = true) }?.let { line ->
                line.trim().split(Regex("\\s+")).firstOrNull { it.matches(Regex("\\d+")) }?.toIntOrNull()
            } ?: 5

        } catch (e: Exception) {
            e.printStackTrace()
            return 5
        }
    }

    fun disconnectAllChannels() {
        channelPoolLock.lock()
        try {
            channelPool.forEach {
                try {
                    if (it.isConnected) it.disconnect()
                } catch (_: Exception) {}
                totalChannels--
            }
            channelPool.clear()

            execChannelPool.forEach {
                try {
                    if (it.isConnected) it.disconnect()
                } catch (_: Exception) {}
                totalChannels--
            }
            execChannelPool.clear()
            channelAvailable.signalAll()
        } finally {
            channelPoolLock.unlock()
        }
    }

    fun close() {
        disconnectAllChannels()
        disconnect()
    }
}
