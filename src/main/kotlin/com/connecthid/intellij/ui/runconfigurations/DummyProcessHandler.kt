package com.connecthid.intellij.ui.runconfigurations

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import java.io.OutputStream

/**
 * Dummy process handler that supports logging and Stop button cancellation
 */
class DummyProcessHandler : ProcessHandler() {
    @Volatile
    private var stopped = false

    override fun destroyProcessImpl() {
        stopped = true
        notifyTextAvailable("❌ Process stopped by user\n", ProcessOutputTypes.STDOUT)
        notifyProcessTerminated(1)
    }

    override fun detachProcessImpl() {
        stopped = true
        notifyProcessDetached()
    }

    override fun detachIsDefault() = false

    override fun getProcessInput(): OutputStream? = null

    fun isStopped(): Boolean = stopped

    fun log(message: String) {
        notifyTextAvailable("$message\n", ProcessOutputTypes.STDOUT)
    }

    override fun isSilentlyDestroyOnClose() = true

    fun finish() {
        if (!stopped) {
            log("✅ Task finished successfully")
            notifyProcessTerminated(0)
        }
    }
}