// infer-server/src/main/kotlin/io/ybigta/text2sql/infer/server/Listener.kt
package io.ybigta.text2sql.infer.server

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class LLMTrace(
    val systemMessage: String?,
    val userMessage: String?,
    val aiMessage: String?
)

class Langchain4jTraceListener : ChatModelListener {
    private val mutex = Mutex()
    private var _lastTrace: LLMTrace? = null

    val lastTrace: LLMTrace?
        get() = _lastTrace

    override fun onResponse(responseContext: ChatModelResponseContext?) {
        val systemMessage = responseContext?.chatRequest()?.messages()?.find { it is SystemMessage } as SystemMessage?
        val userMessage = responseContext?.chatRequest()?.messages()?.find { it is UserMessage } as UserMessage?
        val aiMessageText = responseContext?.chatResponse()?.aiMessage()?.text()

        val trace = LLMTrace(
            systemMessage = systemMessage?.text(),
            userMessage = userMessage?.singleText(),
            aiMessage = aiMessageText
        )

        runBlocking {
            mutex.withLock {
                _lastTrace = trace
            }
        }
    }

    suspend fun clearTrace() {
        mutex.withLock {
            _lastTrace = null
        }
    }
}