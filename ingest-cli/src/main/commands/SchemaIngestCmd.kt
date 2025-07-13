package io.ybigta.text2sql.ingest.cli.commands

import com.charleskorn.kaml.MultiLineStringStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import io.ybigta.text2sql.ingest.SchemaIngester
import io.ybigta.text2sql.ingest.TableDesc
import io.ybigta.text2sql.ingest.config.IngestConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SchemaIngestCmd : CliktCommand("ingest-schema") {
    private val configFilePath: Path by option("-c", "--config", help = "path of ingest_config.yaml")
        .path()
        .required()


    private val logger = LoggerFactory.getLogger(this::class.java)
    private val prettyJson = Json { prettyPrint = true }

    private val prettyYaml = Yaml(
        configuration = YamlConfiguration(
            singleLineStringStyle = SingleLineStringStyle.Plain,
            multiLineStringStyle = MultiLineStringStyle.Literal
        )
    )

    override fun run() {
        val config = IngestConfig.fromConfigFile(configFilePath)
        val tableDescDir: Path = config.config.resources.schemaMarkdownDir.toAbsolutePath()
        val schemaDocGenerator = SchemaIngester.fromConfig(config)

        val dispatcher = newFixedThreadPoolContext(30, "worker")


        runBlocking(dispatcher) {
            readTableDescFiles(tableDescDir)
                .channelFlowMapAsync(2.seconds) { tableDesc ->
                    schemaDocGenerator.ingest(tableDesc)
                    logger.info("inserted (schema={}, table={})", tableDesc.tableName.schemaName, tableDesc.tableName.tableName)
                }
                .collect()
        }
    }


    private fun readTableDescFiles(baseDir: Path): List<TableDesc> {
        return baseDir
            .listDirectoryEntries("*.{yml,json,yaml}")
            .map { it.normalize() }
            .map { path ->
                val tableDocStr = path.readText()

                val encoder = when {
                    path.pathString.endsWith("yml") -> prettyYaml
                    path.pathString.endsWith("yaml") -> prettyYaml
                    path.pathString.endsWith("json") -> prettyJson
                    else -> throw IllegalStateException("only yaml, json file is supported for table-desc file")
                }

                try {
                    encoder
                        .decodeFromString<TableDesc>(tableDocStr)
                        .also { tableDoc -> logger.info("got schema doc of schema={} table={}", tableDoc.tableName.schemaName, tableDoc.tableName.tableName) }
                } catch (e: Exception) {
                    logger.error("failed to parse ${path}\n cause: ${e.message}")
                    throw e
                }
            }
    }
}

private fun <T, R> List<T>.channelFlowMapAsync(
    interval: Duration,
    transform: suspend (T) -> R
): Flow<R> = channelFlow {
    this@channelFlowMapAsync.forEach { element ->
        launch {
            send(transform(element))
        }
        delay(interval)
    }
}
