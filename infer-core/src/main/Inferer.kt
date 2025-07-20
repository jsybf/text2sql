package io.ybigta.text2sql.infer.core

import io.ybigta.text2sql.infer.core.config.InferConfig
import io.ybigta.text2sql.infer.core.config.LLMEndpointBuilder
import io.ybigta.text2sql.infer.core.logic.generate.SqlGenerateLogic
import io.ybigta.text2sql.infer.core.logic.qa_retrieve.QaRetrieveLogic
import io.ybigta.text2sql.infer.core.logic.qa_retrieve.QaRetrieveRepository
import io.ybigta.text2sql.infer.core.logic.qa_retrieve.QaRetrieveResult
import io.ybigta.text2sql.infer.core.logic.qa_retrieve.TblRetrieveRepository
import io.ybigta.text2sql.infer.core.logic.table_retrieve.TblRetrieveLogic
import io.ybigta.text2sql.infer.core.logic.table_retrieve.TblRetrieveResult
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * @param dialect dialect name of sql that will be generated
 */
class Inferer(
    private val tblRetrieveLogic: TblRetrieveLogic,
    private val qaRetrieveLogic: QaRetrieveLogic,
    private val sqlGenerateLogic: SqlGenerateLogic,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * main entry point of text -> sql
     */
    suspend fun infer(question: Question): InferResult = coroutineScope {

        val retrievedTblList: Deferred<List<TblRetrieveResult>> = async { tblRetrieveLogic.retrieve(question) }
        val retrievedQaList: Deferred<List<QaRetrieveResult>> = async { qaRetrieveLogic.retrieve(question) }

        val generatedSql = sqlGenerateLogic.generateCode(
            question,
            retrievedTblList.await().map { it.tableDesc },
            retrievedQaList.await(),
        )

        return@coroutineScope InferResult(
            question = question.question,
            sql = generatedSql,
            tblRetrivedResults = retrievedTblList.await(),
            qaRetrieveResults = retrievedQaList.await()
        )
    }

    companion object {
        fun fromConfig(config: InferConfig) = Inferer(
            TblRetrieveLogic(
                TblRetrieveRepository(
                    db = config.pgvector,
                    embeddingModel = config.embeddingModel
                ),
                LLMEndpointBuilder.SqlGeneration.buildTblSelectionAdjustEndpoint(config)
            ),
            QaRetrieveLogic(
                QaRetrieveRepository(
                    db = config.pgvector,
                    embeddingModel = config.embeddingModel
                ),
                0.03F, // level 1 
                0.13F, // level 2
                0.15F, // level 3
                0.15F, // level 4
            ),
            SqlGenerateLogic(
                LLMEndpointBuilder.SqlGeneration.buildQuestionEntityExtractionEndpoint(config),
                "postgres"
            )
        )
    }
}

data class InferResult(
    val question: String,
    val sql: String,
    val tblRetrivedResults: List<TblRetrieveResult>,
    val qaRetrieveResults: List<QaRetrieveResult>
)

fun main() {
    val config = Path
        .of("./.docs/infer_config.yaml")
        .let { InferConfig.fromConfigFile(it) }

    val inferer = Inferer.fromConfig(config)

    runBlocking(Dispatchers.IO) {
        val q = Question.fromConfig("개선대책담당자중 가장 많은 개선대책을 담당한 사람을 찾아줘", config)
        val result = inferer.infer(q)

        val resultJson = buildJsonObject {
            put("question", result.question)
            put("sql", result.sql)
            putJsonArray("retrieved_qa") {
                result.qaRetrieveResults.forEach { qa ->
                    addJsonObject {
                        put("distance", qa.dist)
                        put("question", qa.qa.question)
                    }
                }
            }
            putJsonArray("retrieved_tbl") {
                result.tblRetrivedResults.sortedBy { it.distance }.forEach { tbl ->
                    addJsonObject {
                        put("distance", tbl.distance)
                        put("embedding_category", tbl.category.name)
                        put("table", tbl.tableDesc.tableName.tableName)
                    }
                }
            }
        }

        Json { prettyPrint = true }
            .encodeToString(resultJson)
            .also { println(it) }

    }
}