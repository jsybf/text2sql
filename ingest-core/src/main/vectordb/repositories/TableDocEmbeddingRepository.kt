package io.ybigta.text2sql.ingest.vectordb.repositories

import dev.langchain4j.model.embedding.EmbeddingModel
import io.ybigta.text2sql.ingest.TableDesc
import io.ybigta.text2sql.ingest.vectordb.tables.TableDocEmbddingTbl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class TableDocEmbeddingRepository(
    private val db: Database,
    private val embeddingModel: EmbeddingModel
) {
    suspend fun insertDescriptionCategory(tableDocId: Int, tableDesc: TableDesc) = newSuspendedTransaction(db = db) {
        val description = tableDesc.toDescription()
        val embedding = embeddingModel.embed(description).content().vector()

        TableDocEmbddingTbl.insertAndGetId {
            it[TableDocEmbddingTbl.tableDoc] = tableDocId
            it[TableDocEmbddingTbl.embedding] = embedding
            it[TableDocEmbddingTbl.data] = description
            it[TableDocEmbddingTbl.embeddingCategory] = TableDocEmbddingTbl.EmbeddingCategory.SUMMARY
        }
    }

    suspend fun insertEntityCategory(tableDocId: Int, tableDesc: TableDesc) = newSuspendedTransaction(db = db) {
        tableDesc.entities.forEach { entity ->
            val embedding = embeddingModel.embed(entity).content().vector()
            TableDocEmbddingTbl.insertAndGetId {
                it[TableDocEmbddingTbl.tableDoc] = tableDocId
                it[TableDocEmbddingTbl.embedding] = embedding
                it[TableDocEmbddingTbl.data] = entity
                it[TableDocEmbddingTbl.embeddingCategory] = TableDocEmbddingTbl.EmbeddingCategory.ENTITY
            }
        }
    }

    suspend fun insertAllCategories(tableDocId: Int, tableDesc: TableDesc) = coroutineScope {
        launch { insertDescriptionCategory(tableDocId, tableDesc) }
        insertEntityCategory(tableDocId, tableDesc)
    }
}