package com.connecthid.intellij.services




class ConnectHidServiceImpl {
    private val connectionService
            by lazy { ServerConnectionService() }
     fun getServerConnectionService(): ServerConnectionService {
        return connectionService

    }

}