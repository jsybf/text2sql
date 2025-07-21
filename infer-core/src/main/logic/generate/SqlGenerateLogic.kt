package io.ybigta.text2sql.infer.core.logic.generate

import io.ybigta.text2sql.infer.core.JacksonPolicy
import io.ybigta.text2sql.infer.core.Question
import io.ybigta.text2sql.infer.core.logic.qa_retrieve.QaRetrieveResult
import io.ybigta.text2sql.ingest.Qa
import io.ybigta.text2sql.ingest.TableDesc
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import org.slf4j.LoggerFactory

/**
 * @param dialect dialect name of sql that will be generated
 */
class SqlGenerateLogic(
    private val sqlGenerationEndpoint: SqlGenerationEndpoint,
    private val dialect: String
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val xml = XML {
        policy = JacksonPolicy
        indentString = "    "
    }

    fun generateCode(
        question: Question,
        tableSchemaList: List<TableDesc>,
        qaList: List<QaRetrieveResult>,
    ): String {
        val payload = SqlGenerationEndpoint.Payload(
            databaseDialect = dialect,
            question = question.question,
            tableDescList = tableSchemaList,
            similarQuestionAnswerPairs = qaList.map { Qa(it.qa.question, it.answer) }
        )

        logger.info("requesting sql generation for question: {}", question.question)
        return sqlGenerationEndpoint.request(xml.encodeToString(payload))
    }
}

interface SqlGenerationEndpoint {
    fun request(request: String): String

    @Serializable
    data class Payload(
        val databaseDialect: String,
        val question: String,
        val tableDescList: List<TableDesc>,
        val similarQuestionAnswerPairs: List<Qa>
    )
}

