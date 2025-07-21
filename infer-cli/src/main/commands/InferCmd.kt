package io.ybigta.text2sql.infer.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.ybigta.text2sql.infer.core.Inferer
import io.ybigta.text2sql.infer.cli.Langchain4jLogger
import io.ybigta.text2sql.infer.core.Question
import io.ybigta.text2sql.infer.core.config.InferConfig
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

internal class InferCmd : CliktCommand("infer") {
    private val configFile: Path by option("-c", "--config", help = "path of ingest_config.yaml").path().required()
    private val question: String by option("-q", "--question", help = "input question").required()
    private val workerNum by option("-w", "--worker", help = "number of thread used for llm request").int().default(5)
    private val trace: Boolean by option("-t", "--trace").flag()

    private val dispatcher: ExecutorCoroutineDispatcher by lazy { newFixedThreadPoolContext(workerNum, "worker") }

    override fun run() = runBlocking(dispatcher) {
        val listeners = if (trace) listOf(Langchain4jLogger()) else emptyList()
        val inferConfig = InferConfig.fromConfigFile(configFile, listeners)
        val inferer = Inferer.fromConfig(inferConfig)

        val inferResult = inferer.infer(Question.fromConfig(question, inferConfig, dispatcher))
        echo(inferResult.sql)
    }
}