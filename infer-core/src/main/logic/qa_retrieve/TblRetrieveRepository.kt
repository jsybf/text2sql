package io.ybigta.text2sql.infer.core.logic.qa_retrieve

import dev.langchain4j.model.embedding.EmbeddingModel
import io.ybigta.text2sql.exposed.pgvector.cosDist
import io.ybigta.text2sql.infer.core.logic.table_retrieve.RetrieveCatgory
import io.ybigta.text2sql.infer.core.logic.table_retrieve.TblRetrieveResult
import io.ybigta.text2sql.ingest.TableDesc
import io.ybigta.text2sql.ingest.TableName
import io.ybigta.text2sql.ingest.vectordb.tables.TableDocEmbddingTbl
import io.ybigta.text2sql.ingest.vectordb.tables.TableDocTbl
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

class TblRetrieveRepository(
    private val db: Database,
    private val embeddingModel: EmbeddingModel
) {
    /**
     * retrieve table required to answer  user's question.
     * @param filterCondition which embedding category to compare with queryText
     */
    fun retrieve(
        queryText: String,
        filterCondition: Set<TableDocEmbddingTbl.EmbeddingCategory>,
        resultN: Int
    ): List<TblRetrieveResult> = transaction(db) {
        val queryTextEmbedding = embeddingModel.embed(queryText).content().vector()
        val distance = (TableDocEmbddingTbl.embedding cosDist queryTextEmbedding).alias("distance")

        val query = TableDocTbl
            .innerJoin(TableDocEmbddingTbl)
            .select(TableDocTbl.schemaJson, distance, TableDocEmbddingTbl.embeddingCategory)
            .where { TableDocEmbddingTbl.embeddingCategory inList filterCondition }
            .orderBy(distance)
            .limit(resultN)

        query.map {
            TblRetrieveResult(
                tableDesc = it[TableDocTbl.schemaJson],
                category = when (it[TableDocEmbddingTbl.embeddingCategory]) {
                    TableDocEmbddingTbl.EmbeddingCategory.SUMMARY -> RetrieveCatgory.SUMMARY
                    TableDocEmbddingTbl.EmbeddingCategory.ENTITY -> RetrieveCatgory.ENTITY
                },
                distance = it[distance]
            )
        }
    }

    fun findTblByName(tblName: TableName): TableDesc? = transaction(db) {
        TableDocTbl
            .select(TableDocTbl.schemaJson)
            .where { (TableDocTbl.schema eq tblName.schemaName) and (TableDocTbl.table eq tblName.tableName) }
            .limit(1)
            .singleOrNull()
            ?.let { it[TableDocTbl.schemaJson] }
    }
}