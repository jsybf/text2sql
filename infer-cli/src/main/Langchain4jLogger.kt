package io.ybigta.text2sql.infer.cli

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Langchain4jLogger : ChatModelListener {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun onResponse(responseContext: ChatModelResponseContext?) {
        val systemMessage = responseContext?.chatRequest()?.messages()?.find { it is SystemMessage } as SystemMessage?
        val userMessage = responseContext?.chatRequest()?.messages()?.find { it is UserMessage } as UserMessage?
        val message = responseContext?.chatResponse()?.aiMessage()?.text()

        val logConent = buildString {
            appendLine()
            appendLine("system_message:")
            appendLine(systemMessage?.text()?.prependIndent("    "))
            appendLine()
            appendLine("user_message:")
            appendLine(userMessage?.singleText()?.prependIndent("    "))
            appendLine("response:")
            appendLine(message?.prependIndent("    "))
        }.prependIndent("    ")

        logger.trace(logConent)
    }
}