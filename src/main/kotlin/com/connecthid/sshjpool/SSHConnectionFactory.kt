package com.connecthid.sshjpool

import org.slf4j.LoggerFactory
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject

class SSHConnectionFactory(
    private val host: String,
    private val username: String,
    private val password: String,
    private val maxChannelsPerConnection: Int = 10
) : BasePooledObjectFactory<SSHConnection>() {
    val LOG = LoggerFactory.getLogger(SSHConnectionFactory::class.java)

    override fun create(): SSHConnection {
        LOG.info("Creating new SSH connection to $host")
        return SSHConnection(host, username, password, maxChannelsPerConnection)
    }

    override fun wrap(obj: SSHConnection): PooledObject<SSHConnection> {
        LOG.info("Wrapping SSH connection to $host")
        return DefaultPooledObject(obj)
    }

    override fun destroyObject(p: PooledObject<SSHConnection>) {
        LOG.info("Destroying SSH connection to $host")
        p.`object`.close()
    }

    override fun validateObject(p: PooledObject<SSHConnection>): Boolean {
        LOG.info("Validating SSH connection to $host")
        return p.`object`.isAlive()
    }
}
