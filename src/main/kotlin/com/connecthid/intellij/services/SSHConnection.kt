package com.connecthid.intellij.services

import com.jcraft.jsch.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64


class SSHConnection(
    private val host: String,
    private val username: String,
    private val password: String? = null,
    private val port: Int = 22,
    private val privateKeyPath: String? = null
) {
    private val jsch = JSch()
    private var session: Session? = null
    private var channel: Channel? = null

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
                session?.setPassword(password)
            } else {
                throw IOException("No authentication method provided")
            }
            
            session?.connect()
        } catch (e: JSchException) {
            throw IOException("Failed to connect to $host: ${e.message}", e)
        }
    }


    fun disconnect() {
        channel?.disconnect()
        session?.disconnect()
    }

    fun isConnected(): Boolean {
        return session?.isConnected == true
    }

    fun getSession(): Session? {
        return session
    }

    fun execute(command: String): String {
        if (!isConnected()) {
            connect()
        }

        try {
            channel = session?.openChannel("exec")
            (channel as ChannelExec).setCommand(command)
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
            channel?.disconnect()
        }
    }

    fun uploadFile(localPath: String, remotePath: String) {
        if (!isConnected()) {
            connect()
        }

        try {
            channel = session?.openChannel("sftp")
            channel?.connect()
            val sftp = channel as ChannelSftp
            sftp.put(localPath, remotePath)
        } catch (e: JSchException) {
            throw IOException("Failed to upload file: ${e.message}", e)
        } catch (e: SftpException) {
            throw IOException("Failed to upload file: ${e.message}", e)
        } finally {
            channel?.disconnect()
        }
    }

    fun downloadFile(remotePath: String, localPath: String) {
        if (!isConnected()) {
            connect()
        }

        try {
            channel = session?.openChannel("sftp")
            channel?.connect()
            val sftp = channel as ChannelSftp
            sftp.get(remotePath, localPath)
        } catch (e: JSchException) {
            throw IOException("Failed to download file: ${e.message}", e)
        } catch (e: SftpException) {
            throw IOException("Failed to download file: ${e.message}", e)
        } finally {
            channel?.disconnect()
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

    fun close() {
        disconnect()
    }
} 