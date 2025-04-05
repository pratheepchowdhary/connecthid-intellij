package com.connecthid.intellij.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jcraft.jsch.ChannelExec
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class MonitoringService(private val project: Project) {
    private val connectionService = ServerConnectionService(project)
    private val meterRegistries = ConcurrentHashMap<String, MeterRegistry>()
    private val monitoringStatus = ConcurrentHashMap<String, Boolean>()

    data class SystemMetrics(
        val cpuUsage: Double,
        val memoryUsage: Double,
        val diskUsage: Double,
        val networkIn: Long,
        val networkOut: Long,
        val uptime: Long,
        val loadAverage: List<Double>
    )

    fun startMonitoring(host: String) {
        if (!connectionService.isConnected(host)) {
            throw IllegalStateException("Not connected to host: $host")
        }

        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        meterRegistries[host] = registry
        monitoringStatus[host] = true

        // Start collecting metrics in a background thread
        Thread {
            while (monitoringStatus[host] == true) {
                try {
                    val metrics = collectMetrics(host)
                    updateMetrics(host, metrics)
                    Thread.sleep(5000) // Collect every 5 seconds
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }.start()
    }

    fun stopMonitoring(host: String) {
        monitoringStatus[host] = false
        meterRegistries[host]?.close()
        meterRegistries.remove(host)
    }

    private fun collectMetrics(host: String): SystemMetrics {
        val session = connectionService.getSession(host)
        val channel = session!!.openChannel("exec") as ChannelExec

        try {
            // CPU usage
            channel.setCommand("top -bn1 | grep 'Cpu(s)' | awk '{print $2 + $4}'")
            channel.connect()
            val cpuUsage = BufferedReader(InputStreamReader(channel.inputStream))
                .readLine()
                .toDouble()

            // Memory usage
            channel.setCommand("free | grep Mem | awk '{print $3/$2 * 100.0}'")
            val memoryUsage = BufferedReader(InputStreamReader(channel.inputStream))
                .readLine()
                .toDouble()

            // Disk usage
            channel.setCommand("df / | tail -1 | awk '{print $5}' | sed 's/%//'")
            val diskUsage = BufferedReader(InputStreamReader(channel.inputStream))
                .readLine()
                .toDouble()

            // Network usage
            channel.setCommand("cat /proc/net/dev | grep eth0 | awk '{print $2,$10}'")
            val networkStats = BufferedReader(InputStreamReader(channel.inputStream))
                .readLine()
                .split(" ")
            val networkIn = networkStats[0].toLong()
            val networkOut = networkStats[1].toLong()

            // Uptime
            channel.setCommand("cat /proc/uptime | awk '{print $1}'")
            val uptime = BufferedReader(InputStreamReader(channel.inputStream))
                .readLine()
                .toDouble()
                .toLong()

            // Load average
            channel.setCommand("cat /proc/loadavg | awk '{print $1,$2,$3}'")
            val loadAverage = BufferedReader(InputStreamReader(channel.inputStream))
                .readLine()
                .split(" ")
                .map { it.toDouble() }

            return SystemMetrics(
                cpuUsage = cpuUsage,
                memoryUsage = memoryUsage,
                diskUsage = diskUsage,
                networkIn = networkIn,
                networkOut = networkOut,
                uptime = uptime,
                loadAverage = loadAverage
            )
        } finally {
            channel.disconnect()
        }
    }

    private fun updateMetrics(host: String, metrics: SystemMetrics) {
        val registry = meterRegistries[host] ?: return
        val tags = Tags.of("host", host)

        registry.gauge("system.cpu.usage", tags, metrics.cpuUsage)
        registry.gauge("system.memory.usage", tags, metrics.memoryUsage)
        registry.gauge("system.disk.usage", tags, metrics.diskUsage)
        registry.gauge("system.uptime", tags, metrics.uptime.toDouble())
        
        registry.counter("system.network.bytes.in", tags).increment(metrics.networkIn.toDouble())
        registry.counter("system.network.bytes.out", tags).increment(metrics.networkOut.toDouble())
        
        metrics.loadAverage.forEachIndexed { index, value ->
            registry.gauge("system.load.average.${index + 1}", tags, value)
        }
    }

    fun getMetrics(host: String): String {
        val registry = meterRegistries[host] as? PrometheusMeterRegistry
            ?: throw IllegalStateException("No metrics available for host: $host")
        return registry.scrape()
    }

    fun isMonitoring(host: String): Boolean {
        return monitoringStatus[host] ?: false
    }
} 