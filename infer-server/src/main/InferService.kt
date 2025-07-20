package io.ybigta.text2sql.infer.server

import io.ybigta.text2sql.infer.core.InferResult
import io.ybigta.text2sql.infer.core.Inferer
import io.ybigta.text2sql.infer.core.Question
import io.ybigta.text2sql.infer.core.config.InferConfig
import io.ybigta.text2sql.infer.core.logic.qa_retrieve.QaRetrieveResult
import io.ybigta.text2sql.infer.core.logic.table_retrieve.RetrieveCatgory
import io.ybigta.text2sql.infer.core.logic.table_retrieve.TblRetrieveResult
import io.ybigta.text2sql.ingest.TableName
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

class InferService(
    private val inferer: Inferer,
    private val inferConfig: InferConfig
) {
    suspend fun infer(question: String): InferResp = coroutineScope {
        val q = Question.fromConfig(question, inferConfig)

        val inferResult = inferer.infer(q)

        val inferResp = InferResp.from(inferResult)

        return@coroutineScope inferResp
    }
}

@Serializable
data class InferResp(
    val question: String,
    val sql: String,
    val tblRetriveResults: List<TblRetrieveResp>,
    val qaRetrieveResps: List<QaRetrieveResp>
) {
    companion object {
        fun from(inferResult: InferResult) = InferResp(
            question = inferResult.question,
            sql = inferResult.sql,
            tblRetriveResults = inferResult.tblRetrivedResults.map { TblRetrieveResp.from(it) },
            qaRetrieveResps = inferResult.qaRetrieveResults.map { QaRetrieveResp.from(it) }
        )
    }
}

@Serializable
data class TblRetrieveResp(
    val tableName: TableName,
    val embeddingCategory: RetrieveCatgory,
    val distance: Float?
) {
    companion object {
        fun from(tblRetrieve: TblRetrieveResult) = TblRetrieveResp(
            tableName = tblRetrieve.tableDesc.tableName,
            embeddingCategory = tblRetrieve.category,
            distance = tblRetrieve.distance
        )
    }
}

@Serializable
data class QaRetrieveResp(
    val question: String,
    val dist: Float,
    val searchLevel: Int
) {
    companion object {
        fun from(qaRetrieveResult: QaRetrieveResult) = QaRetrieveResp(
            question = qaRetrieveResult.qa.question,
            dist = qaRetrieveResult.dist,
            searchLevel = qaRetrieveResult.searchLevel
        )
    }
}
