package io.ybigta.text2sql.infer.core.logic.table_retrieve

import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.ybigta.text2sql.infer.core.Question
import io.ybigta.text2sql.infer.core.logic.qa_retrieve.TblRetrieveRepository
import io.ybigta.text2sql.ingest.TableDesc
import io.ybigta.text2sql.ingest.TableName
import io.ybigta.text2sql.ingest.vectordb.tables.TableDocEmbddingTbl.EmbeddingCategory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory


enum class RetrieveCatgory {
    SUMMARY, ENTITY, RULE
}

data class TblRetrieveResult(
    val category: RetrieveCatgory,
    val distance: Float?,
    val tableDesc: TableDesc
)

class TblRetrieveLogic(
    private val tblRetrieveRepository: TblRetrieveRepository,
    private val tblSelectionAdjustEndpoint: TblSelectionAdjustEndpoint,
    private val resultN: Int = 4
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun retrieve(question: Question): List<TblRetrieveResult> = coroutineScope {
        val retrievedTbls = retrieveTblBySimi(question)
        logger.debug("retrived table from vectordb")
        // add or remove tables from retrievedTbls by llm
        val adjustedTbls = tblSelectionAdjustEndpoint.request(question.question, retrievedTbls.map { it.tableDesc })
        logger.debug("adjusted table selection by llm")
        // find table description for newly added table(category = RULE)
        val tblAddedByRule = adjustedTbls
            .filter { it !in retrievedTbls.map { it.tableDesc.tableName } }
            .map { tblName ->
                val tableDesc = tblRetrieveRepository.findTblByName(tblName)!!
                TblRetrieveResult(
                    category = RetrieveCatgory.RULE,
                    distance = null,
                    tableDesc = tableDesc
                )
            }

        retrievedTbls + tblAddedByRule
    }

    private suspend fun retrieveTblBySimi(question: Question): List<TblRetrieveResult> = coroutineScope {
        val retrieveRequests: List<List<Deferred<List<TblRetrieveResult>>>> = listOf(
            // listOf(
            //     async {
            //         tblRetrieveRepository.retrieve(
            //             queryText = question.normalizedQ.await(),
            //             filterCondition = setOf(EmbeddingCategory.SUMMARY),
            //             resultN = resultN
            //         )
            //     },
            // ),
            question.extractedEntities.await()
                .map { entity ->
                    async {
                        tblRetrieveRepository.retrieve(
                            queryText = entity,
                            filterCondition = setOf(EmbeddingCategory.ENTITY),
                            resultN = resultN
                        )
                    }
                }
        )
        // merge selected tables
        retrieveRequests
            .flatten()
            .awaitAll()
            .flatten()
            .distinctBy { it.tableDesc.tableName }
    }
}

interface TblSelectionAdjustEndpoint {
    @UserMessage(
        """
            question: {{ question }}
            tables: {{ tables }}
        """
    )
    fun request(
        @V("question") question: String,
        @V("tables") tableNames: List<TableDesc>
    ): List<TableName>
}
