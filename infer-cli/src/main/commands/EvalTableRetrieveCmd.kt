package io.ybigta.text2sql.infer.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import io.ybigta.text2sql.infer.cli.channelFlowMapAsync
import io.ybigta.text2sql.infer.cli.readJson
import io.ybigta.text2sql.infer.cli.writeJson
import io.ybigta.text2sql.infer.core.Question
import io.ybigta.text2sql.infer.core.config.InferConfig
import io.ybigta.text2sql.infer.core.config.LLMEndpointBuilder
import io.ybigta.text2sql.infer.core.logic.qa_retrieve.TblRetrieveRepository
import io.ybigta.text2sql.infer.core.logic.table_retrieve.RetrieveCatgory
import io.ybigta.text2sql.infer.core.logic.table_retrieve.TblRetrieveLogic
import io.ybigta.text2sql.infer.core.logic.table_retrieve.TblRetrieveResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@Serializable
private data class TestData(
    val question: String,
    val tables: List<String>
)

@Serializable
private data class ResultFileSpec(
    val summary: SummarySpec,
    val results: List<ResultData>
) {
    @Serializable
    data class SummarySpec(
        val distPerCategory: List<DistancePerCategoryMatric>,
        val precision: PrecisionMatric
    )

    @Serializable
    data class DistancePerCategoryMatric(
        val category: RetrieveCatgory,
        val avgDistance: Float,
        val count: Int
    )

    @Serializable
    data class PrecisionMatric(
        @SerialName("EXACT")
        val exactMatch: Int,
        @SerialName("EXTRA")
        val foundExtraTable: Int,
        @SerialName("MISSED")
        val missedTable: Int,
        @SerialName("EXTRA_AND_MISSED")
        val foundExtraTableAndMissedTable: Int
    )

    @Serializable
    data class ResultData(
        val question: String,
        val answerTables: List<String>,
        val retrievedTables: List<RetrieveResult>,
    )

    @Serializable
    data class RetrieveResult(
        val table: String,
        val category: RetrieveCatgory,
        val distance: Float?
    ) {
        companion object {
            fun from(retrieveResult: TblRetrieveResult) = RetrieveResult(
                table = "${retrieveResult.tableDesc.tableName.schemaName}.${retrieveResult.tableDesc.tableName.tableName}",
                category = retrieveResult.category,
                distance = retrieveResult.distance
            )
        }
    }
}


internal class EvalTableRetrieveCmd() : CliktCommand("eval-table-retrieve") {
    private val inferConfigFile: Path by option("-c", "--config", help = "path of ingest_config.yaml").path().required()
    private val testDataFile: Path by option("-d", "--dataset", help = "path of dataset.json").path().required()
    private val outputFile: Path by option("-o", "--out", help = "path of dataset.json").path().required()
    private val interval by option("-t", "--interval", help = "interval(ms) of llm request").long().default(2000L)
    private val workerNum by option("-w", "--worker", help = "number of thread used for llm request").int().default(30)
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val dispatcher by lazy { newFixedThreadPoolContext(workerNum, "worker") }

    override fun run(): Unit = runBlocking(dispatcher) {
        val inferConfig = InferConfig.fromConfigFile(inferConfigFile)
        val testDataSet = readJson<List<TestData>>(testDataFile, logger)!!

        val tblRetrieveLogic = TblRetrieveLogic(
            TblRetrieveRepository(
                db = inferConfig.pgvector,
                embeddingModel = inferConfig.embeddingModel
            ),
            LLMEndpointBuilder.SqlGeneration.buildTblSelectionAdjustEndpoint(inferConfig)
        )
        // retrieve tables
        val processedCnt = AtomicInteger(0)
        val retrieveResults = testDataSet.channelFlowMapAsync(interval.milliseconds) { data ->
            val question = Question.fromConfig(data.question, inferConfig, dispatcher)
            val retrievedTables = tblRetrieveLogic.retrieve(question)

            logger.info("done {}/{}", processedCnt.incrementAndGet(), testDataSet.count())
            val output = ResultFileSpec.ResultData(
                question = data.question,
                answerTables = data.tables,
                retrievedTables = retrievedTables.map { ResultFileSpec.RetrieveResult.from(it) }
            )
            output
        }
            .toList()

        val resultFileText = ResultFileSpec(
            ResultFileSpec.SummarySpec(
                evalDistancePerCategorySpec(retrieveResults),
                evalPrecision(retrieveResults),
            ),
            retrieveResults
        )
        writeJson(resultFileText, outputFile, logger)
    }

    private fun evalPrecision(outputData: List<ResultFileSpec.ResultData>): ResultFileSpec.PrecisionMatric {
        var exactMatch: Int = 0
        var misfound: Int = 0
        var unfoundAndmisfound: Int = 0
        var unfound: Int = 0

        outputData.forEach { data ->
            val retrivedTables = data.retrievedTables.map { it.table }

            when {
                retrivedTables == data.answerTables -> exactMatch++
                retrivedTables.containsAll(data.answerTables) -> misfound++
                data.answerTables.containsAll(retrivedTables) -> unfound++
                else -> unfoundAndmisfound++
            }
        }

        return ResultFileSpec.PrecisionMatric(
            exactMatch = exactMatch,
            foundExtraTable = misfound,
            missedTable = unfoundAndmisfound,
            foundExtraTableAndMissedTable = unfound
        )
    }

    private fun evalDistancePerCategorySpec(outputData: List<ResultFileSpec.ResultData>): List<ResultFileSpec.DistancePerCategoryMatric> {
        return outputData
            .flatMap { data -> data.retrievedTables.filter { retrieveTable -> data.answerTables.contains(retrieveTable.table) } }
            .groupBy { it.category }
            .map { (category, retrievedTable) ->
                ResultFileSpec.DistancePerCategoryMatric(
                    category = category,
                    avgDistance = retrievedTable.map { it.distance }.filterNotNull().average().toFloat(),
                    count = retrievedTable.count()
                )
            }
    }

}
