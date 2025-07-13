package io.ybigta.text2sql.ingest

import io.ybigta.text2sql.ingest.config.IngestConfig
import io.ybigta.text2sql.ingest.vectordb.repositories.TableDocEmbeddingRepository
import io.ybigta.text2sql.ingest.vectordb.repositories.TableDocRepository
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * read table information from makrdown documentations then insert into vectordb.
 */
class SchemaIngester(
    private val schemaDocRepository: TableDocRepository,
    private val tableDocEmbeddingRepository: TableDocEmbeddingRepository,
) {
    companion object {
        fun fromConfig(ingestConfig: IngestConfig) = SchemaIngester(
            schemaDocRepository = TableDocRepository(ingestConfig.pgvector, ingestConfig.embeddingModel),
            tableDocEmbeddingRepository = TableDocEmbeddingRepository(ingestConfig.pgvector, ingestConfig.embeddingModel),
        )
    }

    private val logger = LoggerFactory.getLogger(this::class.java)


    suspend fun ingest(tableDesc: TableDesc) {
        val id = schemaDocRepository.insertAndGetId(tableDesc)
        tableDocEmbeddingRepository.insertAllCategories(id, tableDesc)
    }
}

@Serializable
data class TableName(
    val schemaName: String,
    val tableName: String
)

@Serializable
data class TblRelation(
    val fromTbl: Column,
    val toTbl: Column,
    val relationType: RelationType,
) {
    @Serializable
    data class Column(
        val schema: String,
        val table: String,
        val column: String,
    )

    class DBMLTblRelationParseException(val msg: String) : Exception(msg)

    companion object {
        private val dbmlSpec = Regex("""\s*(.+)\.(.+)\.(.+)\s+([<->])\s+(.+)\.(.+)\.(.+)\s*""")
        fun parseDBML(dbml: String): Result<TblRelation> = runCatching {
            dbmlSpec
                .find(dbml)
                ?.destructured
                ?.let { (fromSchema, fromTbl, fromCol, typeStr, toSchema, toTbl, toCol) ->
                    TblRelation(
                        fromTbl = Column(
                            fromSchema,
                            fromTbl,
                            fromCol
                        ),
                        toTbl = Column(
                            toSchema,
                            toTbl,
                            toCol
                        ),
                        relationType = RelationType.fromDBML(typeStr),
                    )
                }
                ?: throw DBMLTblRelationParseException("failed to parse '$dbml'")
        }
    }
}

enum class RelationType(val dbml: String) {
    ONE_TO_ONE("-"), MANY_TO_ONE(">"), ONE_TO_MANY("<");

    class RelationTypeParseException(val msg: String) : Exception(msg)
    companion object {
        fun fromDBML(dbml: String): RelationType =
            RelationType.entries.find { it.dbml == dbml } ?: throw RelationTypeParseException("can't map '$dbml' to dbml relation expression")
    }
}

@Serializable
data class TableDesc(
    val tableName: TableName,
    val embeddingSummary: String,
    val description: String,
    val connectedTables: List<TblRelation>,
    val columns: List<Column>,
    val entities: List<String>,
) {
    @Serializable
    data class TblRelation(
        val relationship: String,
        val notes: String
    )

    @Serializable
    data class Column(
        val column: String,
        val description: String
    )

    fun toDescription(): String = """
    table: ${this.tableName}
    
    # description
    ${this.embeddingSummary}
    """.trimIndent()

    fun toDescriptionWithDependencies(): String = """
    table: ${this.tableName}
    
    # description
    ${this.embeddingSummary}
    """.trimIndent()
}
