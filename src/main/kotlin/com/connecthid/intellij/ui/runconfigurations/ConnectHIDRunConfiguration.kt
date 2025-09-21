package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.PluginBundle.message
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.text.StringUtilRt.notNullize
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.lang.Boolean
import java.nio.file.Files
import java.nio.file.Path
import kotlin.String

class ConnectHIDRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String?,
    val task: RunConfigurationTask
) : RunConfigurationBase<ConnectHIDRunConfigurationOptions>(project, factory, name) {
    @NonNls
    val TAG_PREFIX: String = "INDEPENDENT_"
    @NonNls
    val SCRIPT_TEXT_TAG: String = "SCRIPT_TEXT"
    @NonNls
    val SCRIPT_PATH_TAG: String = "SCRIPT_PATH"
    @NonNls
    val SCRIPT_OPTIONS_TAG: String = "SCRIPT_OPTIONS"
    @NonNls
    val SCRIPT_WORKING_DIRECTORY_TAG: String = "SCRIPT_WORKING_DIRECTORY"
    @NonNls
    val INTERPRETER_PATH_TAG: String = "INTERPRETER_PATH"
    @NonNls
    val INTERPRETER_OPTIONS_TAG: String = "INTERPRETER_OPTIONS"
    @NonNls
    private val EXECUTE_IN_TERMINAL_TAG: String = "EXECUTE_IN_TERMINAL"
    @NonNls
    private val EXECUTE_SCRIPT_FILE_TAG: String = "EXECUTE_SCRIPT_FILE"

    @NonNls
    private val SERVER_TAG: String = "SERVER"

    private val LOCALTARGET: String = "LOCALTARGET"
    private val REMOTETARGET:String = "REMOTETARGET"
    private val REMOTEFOLDER: String = "REMOTEFOLDER"
    private val LOCALFOLDER: String = "LOCALFOLDER"



    private  var myScriptText = ""
    private  var myExecuteScriptFile = true
    private  var myScriptPath = ""
    private  var myScriptOptions = ""
    private  var myInterpreterPath = ""
    private  var myInterpreterOptions = ""
    private  var myScriptWorkingDirectory = ""
    private  var myExecuteInTerminal = true
    private  var server: String = ""
    private  var myEnvData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT
    private  var localTarget: String=""
    private  var remoteTarget: String=""
    private  var remoteFolder: String=""
    private  var localFolder: String=""


    override fun getOptions(): ConnectHIDRunConfigurationOptions {
        return super.getOptions() as ConnectHIDRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
        return  ConnectHIDSettingsEditor(project,task)
    }

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        val scriptPath = Path.of(myScriptPath)
        if (myExecuteScriptFile) {
            if (!Files.exists(scriptPath)) {
                throw RuntimeConfigurationError(message("sh.run.script.not.found"))
            }
            if (StringUtil.isNotEmpty(myInterpreterPath) || !Files.isExecutable(scriptPath)) {
                val interpreterPath = Path.of(myInterpreterPath)
                if (!Files.exists(interpreterPath)) {
                    throw RuntimeConfigurationError(message("sh.run.interpreter.not.found"))
                }
                if (!Files.isExecutable(interpreterPath)) {
                    throw RuntimeConfigurationError(message("sh.run.interpreter.should.be.executable"))
                }
            }
        }
        if (!Files.exists(Path.of(myScriptWorkingDirectory))) {
            throw RuntimeConfigurationError(message("sh.run.working.dir.not.found"))
        }
    }




    public override fun writeExternal(  element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, SCRIPT_TEXT_TAG, myScriptText)
        writePathWithMetadata(element, myScriptPath, SCRIPT_PATH_TAG)
        JDOMExternalizerUtil.writeField(element, SCRIPT_OPTIONS_TAG, myScriptOptions)
        writePathWithMetadata(element, myScriptWorkingDirectory, SCRIPT_WORKING_DIRECTORY_TAG)
        writePathWithMetadata(element, myInterpreterPath, INTERPRETER_PATH_TAG)
        JDOMExternalizerUtil.writeField(element, INTERPRETER_OPTIONS_TAG, myInterpreterOptions)
        JDOMExternalizerUtil.writeField(element, EXECUTE_IN_TERMINAL_TAG, myExecuteInTerminal.toString())
        JDOMExternalizerUtil.writeField(element, EXECUTE_SCRIPT_FILE_TAG, myExecuteScriptFile.toString())
        JDOMExternalizerUtil.writeField(element, SERVER_TAG, server)
        JDOMExternalizerUtil.writeField(element, LOCALTARGET, localTarget)
        JDOMExternalizerUtil.writeField(element, REMOTETARGET, remoteTarget)
        JDOMExternalizerUtil.writeField(element, REMOTEFOLDER, remoteFolder)
        JDOMExternalizerUtil.writeField(element, LOCALFOLDER, localFolder)
        myEnvData.writeExternal(element)
    }

    @Throws(InvalidDataException::class)
    public override fun readExternal(  element: Element) {
        super.readExternal(element)
        myScriptText = readStringTagValue(element, SCRIPT_TEXT_TAG)
        myScriptPath = readPathWithMetadata(element, SCRIPT_PATH_TAG)!!
        myScriptOptions = readStringTagValue(element, SCRIPT_OPTIONS_TAG)
        myScriptWorkingDirectory = readPathWithMetadata(element, SCRIPT_WORKING_DIRECTORY_TAG)!!
        myInterpreterPath = readPathWithMetadata(element, INTERPRETER_PATH_TAG)!!
        myInterpreterOptions = readStringTagValue(element, INTERPRETER_OPTIONS_TAG)
        myExecuteInTerminal =
            JDOMExternalizerUtil.readField(element, EXECUTE_IN_TERMINAL_TAG, Boolean.TRUE.toString()).toBoolean()
        myExecuteScriptFile =
            JDOMExternalizerUtil.readField(element, EXECUTE_SCRIPT_FILE_TAG, Boolean.TRUE.toString()).toBoolean()
        server = readStringTagValue(element, SERVER_TAG)
        localTarget = readStringTagValue(element, LOCALTARGET)
        remoteTarget = readStringTagValue(element, REMOTETARGET)
        remoteFolder = readStringTagValue(element, REMOTEFOLDER)
        localFolder = readStringTagValue(element, LOCALFOLDER)
        myEnvData = EnvironmentVariablesData.readExternal(element)
    }




    private fun getPathByElement(  element: PsiElement?): String? {
        val vfile = PsiUtilCore.getVirtualFile(element)
        if (vfile == null) return null
        return vfile.getPath()
    }

    private fun writePathWithMetadata(  element: Element,   path: String,   pathTag: String) {
        val systemIndependentPath: String = FileUtil.toSystemIndependentName(path)
        JDOMExternalizerUtil.writeField(element, TAG_PREFIX + pathTag, (systemIndependentPath == path).toString())
        JDOMExternalizerUtil.writeField(element, pathTag, systemIndependentPath)
    }

    private fun readPathWithMetadata(  element: Element,   pathTag: String): String? {
        return if (JDOMExternalizerUtil.readField(element, TAG_PREFIX + pathTag).toBoolean())
            readStringTagValue(element, pathTag)
        else
            toSystemDependentName(readStringTagValue(element, pathTag))
    }


    private fun readStringTagValue(  element: Element,   tagName: String): String {
        return notNullize(JDOMExternalizerUtil.readField(element, tagName), "")
    }

    fun getScriptText(): String? {
        return myScriptText
    }

    fun setScriptText(scriptText: String?) {
        myScriptText = scriptText!!
    }

    fun getScriptPath(): String? {
        return myScriptPath
    }

    fun setScriptPath(scriptPath: String) {
        myScriptPath = scriptPath.trim { it <= ' ' }
    }

    fun getScriptOptions(): String? {
        return myScriptOptions
    }

    fun setScriptOptions(scriptOptions: String) {
        myScriptOptions = scriptOptions.trim { it <= ' ' }
    }

    fun getScriptWorkingDirectory(): String? {
        return myScriptWorkingDirectory
    }

    fun getServer(): String{
        return server
    }
    fun setServer(server: String){
        this.server = server
    }

    fun setScriptWorkingDirectory(scriptWorkingDirectory: String) {
        myScriptWorkingDirectory = scriptWorkingDirectory.trim { it <= ' ' }
    }

    fun isExecuteInTerminal(): kotlin.Boolean {
        return myExecuteInTerminal
    }

    fun setExecuteInTerminal(executeInTerminal: kotlin.Boolean) {
        myExecuteInTerminal = executeInTerminal
    }

    fun isExecuteScriptFile(): kotlin.Boolean {
        return myExecuteScriptFile
    }

    fun setExecuteScriptFile(executeScriptFile: kotlin.Boolean) {
        myExecuteScriptFile = executeScriptFile
    }

    fun getEnvData(): EnvironmentVariablesData{
        return myEnvData
    }

    fun setEnvData(envData: EnvironmentVariablesData) {
        myEnvData = envData
    }

    fun getInterpreterPath(): String? {
        return myInterpreterPath
    }

    fun setInterpreterPath( interpreterPath: String) {
        myInterpreterPath = interpreterPath.trim { it <= ' ' }
    }

    fun getInterpreterOptions(): String? {
        return myInterpreterOptions
    }

    fun setInterpreterOptions(interpreterOptions: String) {
        myInterpreterOptions = interpreterOptions.trim { it <= ' ' }
    }

    fun setLocalTarget(localTarget: String){
        this.localTarget = localTarget
    }

    fun getLocalTarget(): String{
        return localTarget
    }

    fun setRemoteTarget(remoteTarget: String){
        this.remoteTarget = remoteTarget
    }

    fun getRemoteTarget(): String{
        return remoteTarget
    }

    fun setRemoteFolder(remoteFolder: String){
        this.remoteFolder = remoteFolder
    }

    fun getRemoteFolder(): String{
        return remoteFolder
    }

    fun setLocalFolder(localFolder: String){
        this.localFolder = localFolder
    }

    fun getLocalFolder(): String{
        return localFolder
    }




    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ): RunProfileState? {
        TODO("Not yet implemented")
    }

}