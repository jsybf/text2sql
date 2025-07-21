package io.ybigta.text2sql.infer.core.logic.qa_retrieve

import dev.langchain4j.model.embedding.EmbeddingModel
import io.ybigta.text2sql.exposed.pgvector.cosDist
import io.ybigta.text2sql.infer.core.Question
import io.ybigta.text2sql.ingest.StructuredQa
import io.ybigta.text2sql.ingest.TableDesc
import io.ybigta.text2sql.ingest.vectordb.tables.QaEmbeddingTbl
import io.ybigta.text2sql.ingest.vectordb.tables.QaEmbeddingTbl.EmbeddingType
import io.ybigta.text2sql.ingest.vectordb.tables.QaTbl
import io.ybigta.text2sql.ingest.vectordb.tables.TableDocTbl
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class QaRetrieveRepository(
    private val db: Database,
    private val embeddingModel: EmbeddingModel,
) {
    fun retrieve(queryText: String, maxDist: Float, embeddingTypes: List<EmbeddingType>, serachLevel: Int): List<QaRetrieveResult> = transaction(db) {
        val nomarliedQEmbedding = embeddingModel.embed(queryText).content().vector()
        val cosDist = (QaEmbeddingTbl.embedding cosDist nomarliedQEmbedding).alias("cos_dist")

        val retrieveResult = QaTbl
            .innerJoin(QaEmbeddingTbl)
            .select(QaTbl.id, QaTbl.question, QaTbl.answer, QaTbl.structuredQa, cosDist)
            .andWhere { QaEmbeddingTbl.embeddingTarget inList embeddingTypes }
            // .andWhere { cosDist lessEq maxDist }
            .orderBy(cosDist)
            .map { rw ->
                Pair(
                    QaTbl.Dto(
                        rw[QaTbl.id].value,
                        rw[QaTbl.question],
                        rw[QaTbl.answer],
                        rw[QaTbl.structuredQa],
                    ),
                    rw[cosDist]
                )
            }
        val schemaDotTableNameFormat = Regex(""".+\.(.+)""")
        retrieveResult
            .filter { (qaDto, dist) -> dist < maxDist }
            .map { (qaDto, dist) ->
                // TODO: llm이 생성한  structuredQa의 dataSource(table)의 이름이 실제와 일치하지 않아 오류발생가능..
                val sourceTblNames: List<String> = qaDto
                    .structuredQa.dataSource
                    .map { it.table }
                    .map { schemaDotTableNameFormat.find(it)?.groupValues?.get(1) ?: it }

                val sourceTblDocs = sourceTblNames.map { name -> findDocByTableName(name)!! }
                QaRetrieveResult(qaDto.structuredQa, qaDto.answer, dist, sourceTblDocs, serachLevel)
            }
    }

    private fun findDocByTableName(tableName: String): TableDesc? = transaction(db) {
        TableDocTbl
            .select(TableDocTbl.schemaJson)
            .where { TableDocTbl.table eq tableName }
            .firstOrNull()
            ?.let { rw -> rw[TableDocTbl.schemaJson] }
    }
}

@Serializable
data class QaRetrieveResult(
    val qa: StructuredQa,
    val answer: String,
    val dist: Float,
    val sourceTables: List<TableDesc>,
    val searchLevel: Int
)

class QaRetrieveLogic(
    private val qaRetrieveRepo: QaRetrieveRepository,
    private val level1MaxDist: Float,
    private val level2MaxDist: Float, // P_intergroup_dist_95
    private val level3MaxDist: Float, // P_intgergroup_dist_99
    private val level4MaxDist: Float,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun retrieve(question: Question): List<QaRetrieveResult> {
        retrieveLevel2(question)
            .also { logger.debug("retrieved qa (level2)") }
            .also { if (it.isNotEmpty()) logger.info("got result from qa-retrieving (level2)") }
            .also { if (it.isNotEmpty()) return it }

        retrieveLevel3(question)
            .also { logger.debug("retrieved qa (level3)") }
            .also { if (it.isNotEmpty()) logger.info("got result from qa-retrieving (level3)") }
            .also { if (it.isNotEmpty()) return it }

        return retrieveLevel4(question)
            .also { logger.debug("retrieved qa (level4)") }
            .also { if (it.isNotEmpty()) logger.info("got result from qa-retrieving (level4)") }
            .also { if (it.isEmpty()) logger.info("got empty result from qa-retrieving (level4)") }
    }


    /**
     * compare normalized_question (input) <-> question ∪ normalized_question (vectordb)
     */
    private suspend fun retrieveLevel2(question: Question): List<QaRetrieveResult> = qaRetrieveRepo.retrieve(
        question.normalizedQ.await(),
        level2MaxDist,
        listOf(EmbeddingType.QUESTION, EmbeddingType.NORMALIZED_QUESTION),
        2
    )

    /**
     * compare normalized_question ∪ main_clause (input) <-> question ∪ normalized_question (vectordb)
     */
    private suspend fun retrieveLevel3(question: Question): List<QaRetrieveResult> = listOf(
        qaRetrieveRepo.retrieve(
            question.normalizedQ.await(),
            level3MaxDist,
            listOf(EmbeddingType.QUESTION, EmbeddingType.NORMALIZED_QUESTION, EmbeddingType.VARIATION),
            3
        ),
        qaRetrieveRepo.retrieve(
            question.mainClause.await(),
            level3MaxDist,
            listOf(EmbeddingType.REQUESTED_ENTITIES),
            3
        )
    )
        .flatten()
        .distinct()

    /**
     * compare normalized_question ∪ main_clause (input) <-> question ∪ normalized_question ∪ extended_question (vectordb)
     */
    private suspend fun retrieveLevel4(question: Question): List<QaRetrieveResult> = listOf(
        qaRetrieveRepo.retrieve(
            question.normalizedQ.await(),
            level4MaxDist,
            listOf(EmbeddingType.QUESTION, EmbeddingType.NORMALIZED_QUESTION, EmbeddingType.VARIATION),
            4
        ),
        qaRetrieveRepo.retrieve(
            question.mainClause.await(),
            level4MaxDist,
            listOf(EmbeddingType.QUESTION, EmbeddingType.NORMALIZED_QUESTION, EmbeddingType.VARIATION),
            4
        )
    )
        .flatten()
        .distinct()

}