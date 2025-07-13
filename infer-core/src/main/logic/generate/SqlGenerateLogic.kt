package io.ybigta.text2sql.infer.core.logic.generate

import io.ybigta.text2sql.infer.core.Question
import io.ybigta.text2sql.infer.core.logic.qa_retrieve.QaRetrieveResult
import io.ybigta.text2sql.ingest.TableDesc
import org.slf4j.LoggerFactory

/**
 * @param dialect dialect name of sql that will be generated
 */
class SqlGenerateLogic(
    private val sqlGenerationEndpoint: SqlGenerationEndpoint,
    private val dialect: String
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun generateCode(
        question: Question,
        tableSchemaList: List<TableDesc>,
        qaList: List<QaRetrieveResult>,
    ): String {
        logger.info("requesting sql generation for question: {}", question.question)
        val requestMsg = buildRequestStr(question, tableSchemaList, qaList, "postgres")
        logger.trace("request payload: \n {}", requestMsg)
        return sqlGenerationEndpoint.request(requestMsg)
    }

    fun buildRequestStr(
        question: Question,
        tableSchemaList: List<TableDesc>,
        qaList: List<QaRetrieveResult>,
        dialect: String
    ): String {
        val tblDescStr = tableSchemaList
            .map { tbl ->
                """
            <table>
                <table_name>${tbl.tableName.schemaName}.${tbl.tableName.tableName}</table_name>
                <description>
                    ${tbl.description}
                </description>
                <columns>
                    ${tbl.columns.map { column -> "column: ${column.column} \n description:${column.description}" }.joinToString("\n")}
                </columns>
            </table>
            """.trimIndent()
            }
            .joinToString()
        val qa = qaList
            .map { qa ->
                """
                <qa>
                    <question>
                        ${qa.qa.question}
                    </question>
                    <answer>
                        ${qa.answer}
                    </answer>
                </qa>
                """.trimIndent()
            }

        return """
        <database_dialect>${dialect}</database_dialect>
        <question>${question}</question>
        <table_description>
            ${tblDescStr}
        </table_description>
        <similiar_question_answer_pair>
            #${qa}
        </similiar_question_answer_pair>
        """.trimIndent()
    }
}

interface SqlGenerationEndpoint {
    fun request(request: String): String
}