package io.ybigta.text2sql.ingest.vectordb.repositories

import dev.langchain4j.model.embedding.EmbeddingModel
import io.ybigta.text2sql.ingest.TableDesc
import io.ybigta.text2sql.ingest.vectordb.tables.TableDocEmbddingTbl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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
            it[TableDocEmbddingTbl.embeddingCategory] = TableDocEmbddingTbl.EmbeddingCategory.DESCRIPTION
        }
    }

    suspend fun insertDescriptionDependenciesCategory(tableDocId: Int, tableDesc: TableDesc) = newSuspendedTransaction(db = db) {
        val descriptionWithDependencies = tableDesc.toDescriptionWithDependencies()
        val embedding = embeddingModel.embed(descriptionWithDependencies).content().vector()
        TableDocEmbddingTbl.insertAndGetId {
            it[TableDocEmbddingTbl.tableDoc] = tableDocId
            it[TableDocEmbddingTbl.embedding] = embedding
            it[TableDocEmbddingTbl.data] = descriptionWithDependencies
            it[TableDocEmbddingTbl.embeddingCategory] = TableDocEmbddingTbl.EmbeddingCategory.DESCRIPTION_DEPENDENCIES
        }
    }

    suspend fun insertTableNameCategory(tableDocId: Int, tableDesc: TableDesc) = newSuspendedTransaction(db = db) {
        val embedding = embeddingModel.embed(tableDesc.tableName.tableName).content().vector()
        TableDocEmbddingTbl.insertAndGetId {
            it[TableDocEmbddingTbl.tableDoc] = tableDocId
            it[TableDocEmbddingTbl.embedding] = embedding
            it[TableDocEmbddingTbl.data] = tableDesc.tableName.tableName
            it[TableDocEmbddingTbl.embeddingCategory] = TableDocEmbddingTbl.EmbeddingCategory.TABLE_NAME
        }
    }


    suspend fun insertConnectedTablesCategory(tableDocId: Int, tableDesc: TableDesc) = newSuspendedTransaction(db = db) {
        val connectedTables = Json.encodeToString(tableDesc.connectedTables)
        val embedding = embeddingModel.embed(connectedTables).content().vector()
        TableDocEmbddingTbl.insertAndGetId {
            it[TableDocEmbddingTbl.tableDoc] = tableDocId
            it[TableDocEmbddingTbl.embedding] = embedding
            it[TableDocEmbddingTbl.data] = connectedTables
            it[TableDocEmbddingTbl.embeddingCategory] = TableDocEmbddingTbl.EmbeddingCategory.CONNECTED_TABLES
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
        launch { insertDescriptionDependenciesCategory(tableDocId, tableDesc) }
        launch { insertTableNameCategory(tableDocId, tableDesc) }
        launch { insertConnectedTablesCategory(tableDocId, tableDesc) }
        insertEntityCategory(tableDocId, tableDesc)
    }
}