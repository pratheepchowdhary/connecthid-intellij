package com.connecthid.intellij.services

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class DockerService(private val project: Project) {
    private val dockerClients = ConcurrentHashMap<String, DockerClient>()
    private val connectionService = ServerConnectionService(project)

    data class ContainerInfo(
        val id: String,
        val name: String,
        val image: String,
        val status: String,
        val ports: List<ContainerPort>,
        val created: Long
    )

    data class ImageInfo(
        val id: String,
        val repository: String,
        val tag: String,
        val size: Long,
        val created: Long
    )

    fun connect(host: String) {
        if (!connectionService.isConnected(host)) {
            throw IllegalStateException("Not connected to host: $host")
        }

        val config: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost("tcp://$host:2375")
            .build()

        val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()

        val dockerClient = DockerClientImpl.getInstance(config, httpClient)
        dockerClients[host] = dockerClient
    }

    fun listContainers(host: String, all: Boolean = false): List<ContainerInfo> {
        val client = getClient(host)
        return client.listContainersCmd()
            .withShowAll(all)
            .exec()
            .map { container ->
                ContainerInfo(
                    id = container.id,
                    name = container.names.firstOrNull()?.removePrefix("/") ?: "",
                    image = container.image,
                    status = container.status,
                    ports = container.ports?.toList() ?: emptyList(),
                    created = container.created
                )
            }
    }

    fun listImages(host: String): List<ImageInfo> {
        val client = getClient(host)
        return client.listImagesCmd()
            .exec()
            .map { image ->
                ImageInfo(
                    id = image.id,
                    repository = image.repoTags?.firstOrNull()?.split(":")?.get(0) ?: "",
                    tag = image.repoTags?.firstOrNull()?.split(":")?.get(1) ?: "latest",
                    size = image.size,
                    created = image.created
                )
            }
    }

    fun startContainer(host: String, containerId: String) {
        val client = getClient(host)
        client.startContainerCmd(containerId).exec()
    }

    fun stopContainer(host: String, containerId: String) {
        val client = getClient(host)
        client.stopContainerCmd(containerId).exec()
    }

    fun removeContainer(host: String, containerId: String, force: Boolean = false) {
        val client = getClient(host)
        client.removeContainerCmd(containerId)
            .withForce(force)
            .exec()
    }

    fun createContainer(host: String, image: String, name: String? = null): CreateContainerResponse {
        val client = getClient(host)
        return client.createContainerCmd(image)
            .withName(name)
            .exec()
    }

    fun pullImage(host: String, image: String) {
        val client = getClient(host)
        client.pullImageCmd(image)
            .exec(object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.PullResponseItem>() {
                override fun onNext(item: PullResponseItem) {
                    // Handle pull progress if needed
                }
            })
            .awaitCompletion()
    }

    private fun getClient(host: String): DockerClient {
        return dockerClients[host] ?: throw IllegalStateException("Docker client not initialized for host: $host")
    }

    fun disconnect(host: String) {
        dockerClients[host]?.close()
        dockerClients.remove(host)
    }
} 