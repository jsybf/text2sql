package io.ybigta.text2sql.ingest

import io.ybigta.text2sql.ingest.config.IngestConfig
import io.ybigta.text2sql.ingest.config.LLMEndpointBuilder
import io.ybigta.text2sql.ingest.llmendpoint.SchemaMarkdownGenerationEndpoint
import io.ybigta.text2sql.ingest.llmendpoint.StructureSchemaDocEndPoint
import io.ybigta.text2sql.ingest.llmendpoint.TableEntitiesExtractionEndpoint
import io.ybigta.text2sql.ingest.logic.doc_gene.DBSchemaIngester
import io.ybigta.text2sql.ingest.logic.doc_gene.PostgresSchemaIngester
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * this logic is not essential part of schema ingest logic.
 * but it helps user to auto-generate [TableDesc] (json file describing table schema) instead of hand-writting them
 */
class TableDescGenerator(
    private val sourceDB: Database,
    private val interval: Duration,
    private val schemaMarkdownGenerationEndpoint: SchemaMarkdownGenerationEndpoint,
    private val tableEntitiesExtractionEndpoint: TableEntitiesExtractionEndpoint,
    private val structureSchemaDocEndpoint: StructureSchemaDocEndPoint
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        fun fromConfig(ingestConfig: IngestConfig, sourceDB: Database, interval: Duration = 3.seconds) = TableDescGenerator(
            sourceDB = sourceDB,
            interval = interval,
            tableEntitiesExtractionEndpoint = LLMEndpointBuilder.SchemaIngest.buildTableEntitiesExtractionEndpoint(ingestConfig),
            schemaMarkdownGenerationEndpoint = LLMEndpointBuilder.SchemaMarkdownGeneration.buildSchemaMarkdownGenerationEndpoint(ingestConfig),
            structureSchemaDocEndpoint = LLMEndpointBuilder.SchemaIngest.buildStrucutureSchemaDocEndpoint(ingestConfig)
        )
    }

    /**
     * only entry point of this class.
     * query source DB for table schema then generate [TableDesc]
     */
    suspend fun generateTableDesc(): Flow<Pair<TableName, TableDesc>> =
        autoGenerateTableDoc(this.interval)
            .map { (tableName, tableDoc) -> Pair(tableName, schemaIngestLogic(tableDoc, tableName)) }

    /**
     * what it does:
     * 1. query table schema information to DB
     * 2. request llm to generate markdown about table schema
     */
    private fun autoGenerateTableDoc(interval: Duration): Flow<Pair<TableName, String>> {
        val dbSchemaIngester: DBSchemaIngester = when (this.sourceDB.vendor) {
            "PostgreSQLNG", "PostgreSQL" -> PostgresSchemaIngester(this.sourceDB)
            else -> throw IllegalStateException("(${this.sourceDB.vendor}) is not supported database")
        }
        return dbSchemaIngester
            .requestTableSchemas()
            .channelFlowMapAsync(interval) { tableSchema ->
                logger.debug("requesting table markdown generation(schema={},table={})", tableSchema.tableName.tableName, tableSchema.tableName.schemaName)
                val tableDoc = schemaMarkdownGenerationEndpoint.request(Json.encodeToJsonElement(tableSchema) as JsonObject)
                Pair(tableSchema.tableName, tableDoc)
            }
    }

    /**
     * extracting entities from table markdown document is done by selecting frequently
     * appeared entities of multiple same [TableEntitiesExtractionEndpoint] request.
     *
     * @param tableDoc markdown document about table schema
     * @param requestNum the number of same llm request to [TableEntitiesExtractionEndpoint].
     * @param entityFrequency the numeber of entity appearance in multiple llm request treated as strong entity
     */
    private suspend fun schemaIngestLogic(
        tableDoc: String,
        tblName: TableName, // for logging
        requestNum: Int = 7,
        entityFrequency: Int = 3,
        interval: Duration = 1.seconds,
    ): TableDesc = coroutineScope {
        logger.debug("reqeusting for transforming table-doc(.md) doc to table-desc(.json) (schema={},table={}) ", tblName.tableName, tblName.schemaName)
        val tableDescWithoutEntities: TableDesc = structureSchemaDocEndpoint.request(tableDoc)

        logger.debug("reqeusting for entities extraction. (schema={},table={},will request {} times) ", requestNum, tblName.tableName, tblName.schemaName)
        // request multiple sam llm request then select most frequent
        val extractedEntitiesList = (1..requestNum)
            .channelFlowMapAsync(interval) { tableEntitiesExtractionEndpoint.request(tableDescWithoutEntities) }
            .toList()

        val entityFrequecies = extractedEntitiesList
            .flatten()
            .groupingBy { it }
            .eachCount()

        val entities = entityFrequecies.filterValues { frequency -> entityFrequency < frequency }.keys.toList()

        return@coroutineScope tableDescWithoutEntities.copy(
            tableName = tblName,
            entities = entities,
        )
    }

}

@Serializable
data class TableSchema(
    val tableName: TableName,
    val columns: List<Map<String, String?>>
)
