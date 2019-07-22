/*
 * Copyright 2019-2019 Simon Ogorodnik.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package me.semoro.tabnine

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.twelvemonkeys.lang.Platform
import com.twelvemonkeys.lang.SystemUtil
import kotlinx.serialization.*
import kotlinx.serialization.internal.HashMapSerializer
import kotlinx.serialization.internal.NamedMapClassDescriptor
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.full.findAnnotation

const val tabNineVersion = "2.0.2"

class TabNineProcess(val process: Process) : AutoCloseable {
    override fun close() {
        outputWriter.close()
        inputReader.close()
        if (process.isAlive) process.destroy()
    }

    val outputWriter: BufferedWriter = process.outputStream.bufferedWriter()
    val inputReader: BufferedReader = process.inputStream.bufferedReader()
}

class TabNineCommunicationHandler : AutoCloseable {

    private val tabNineHome = PluginManager.getPlugin(PluginId.getId("me.semoro.tabnine"))!!.path.resolve("BundledTabNine")


    private val logger = Logger.getInstance(TabNineCommunicationHandler::class.java)

    private var process: TabNineProcess? = null


    private fun selectBinary(): String {
        return when {
            SystemInfo.is32Bit && SystemInfo.isLinux -> "i686-unknown-linux-gnu/TabNine"
            SystemInfo.is64Bit && SystemInfo.isLinux -> "x86_64-unknown-linux-gnu/TabNine"
            SystemInfo.is32Bit && SystemInfo.isMac -> "i686-apple-darwin/TabNine"
            SystemInfo.is64Bit && SystemInfo.isMac -> "x86_64-apple-darwin/TabNine"
            SystemInfo.is32Bit && SystemInfo.isWindows -> "i686-pc-windows-gnu/TabNine.exe"
            SystemInfo.is64Bit && SystemInfo.isWindows -> "x86_64-pc-windows-gnu/TabNine.exe"
            else -> error("TabNine failed to start, unknown OS")
        }
    }



    fun start() {
        val path = tabNineHome.resolve(tabNineVersion).resolve(selectBinary())
        process = TabNineProcess(ProcessBuilder(path.absolutePath).start())
    }

    fun stop() {
        process?.close()
    }

    override fun close() {
        stop()
    }

    private fun ensureStarted() {
        if (process == null) {
            start()
        }
    }

    private val communicationLock = ReentrantLock()



    private fun send(req: String) {
        logger.debug("-> $req")
        with(process!!.outputWriter) {
            write(req)
            newLine()
            flush()
        }
    }

    private fun receive(): String {
        val responseLine = process!!.inputReader.readLine()
        logger.debug(responseLine)
        return responseLine
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    private fun send(requestBody: Protocol.RequestBody) {
        ensureStarted()
        send(Json.stringify(Protocol.ApiRequest(tabNineVersion, Protocol.Request(requestBody))))
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    private inline fun <reified T: Any> receive(): T {
        return Json.parse(receive())
    }


    fun autocomplete(requestBody: Protocol.Autocomplete): Protocol.AutocompleteResponse = communicationLock.withLock {

        send(requestBody)
        return receive<Protocol.AutocompleteResponse>()
    }

    fun prefetch(filename: String) {
        send(Protocol.Prefetch(filename))
        require(receive() == "null")
    }

    companion object {
        fun getInstance(project: Project): TabNineCommunicationHandler {
            return ServiceManager.getService(project, TabNineCommunicationHandler::class.java)
        }
    }

}


object Protocol {

    @Serializable
    data class ApiRequest(val version: String = tabNineVersion, val request: Request)

    @Serializable
    data class Request(val body: RequestBody) {

        @Serializer(forClass = Request::class)
        companion object : KSerializer<Request> {
            override val descriptor: SerialDescriptor =
                NamedMapClassDescriptor("Request", String.serializer().descriptor, RequestBody.serializer().descriptor)

            @ImplicitReflectionSerializer
            override fun serialize(encoder: Encoder, obj: Request) {
                encoder.encodeSerializableValue(HashMapSerializer(
                    String.serializer(),
                    obj.body::class.serializer() as KSerializer<RequestBody>
                ), mapOf(obj.body::class.findAnnotation<RequestBodyName>()!!.value to obj.body))
            }

            override fun deserialize(decoder: Decoder): Request {
                error("not supported")
            }
        }
    }


    @Serializable
    abstract class RequestBody

    @RequestBodyName("Autocomplete")
    @Serializable
    data class Autocomplete(
        val before: String,
        val after: String,
        val filename: String?,
        val region_includes_beginning: Boolean,
        val region_includes_end: Boolean,
        val max_num_results: Int? = null
    ) : RequestBody()

    @RequestBodyName("Prefetch")
    @Serializable
    data class Prefetch(
        val filename: String
    ) : RequestBody()



    @Serializable
    data class AutocompleteResponse(
        val old_prefix: String,
        val results: List<ResultEntry>,
        val user_message: List<String>,
        val docs: List<String>
    )

    @Serializable
    data class ResultEntry(
        val new_prefix: String,
        val old_suffix: String,
        val new_suffix: String,

        val kind: CompletionItemKind? = null,
        val detail: String? = null,
        val documentation: String? = null,
        val deprecated: Boolean? = null
    )


    enum class CompletionItemKind(val id: Int) {
        Text(1),
        Method(2),
        Function(3),
        Constructor(4),
        Field(5),
        Variable(6),
        Class(7),
        Interface(8),
        Module(9),
        Property(10),
        Unit(11),
        Value(12),
        Enum(13),
        Keyword(14),
        Snippet(15),
        Color(16),
        File(17),
        Reference(18),
        Folder(19),
        EnumMember(20),
        Constant(21),
        Struct(22),
        Event(23),
        Operator(24),
        TypeParameter(25)
    }
}


annotation class RequestBodyName(val value: String)


