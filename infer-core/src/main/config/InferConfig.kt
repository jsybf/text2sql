package io.ybigta.text2sql.infer.core.config

import com.charleskorn.kaml.Yaml
import dev.langchain4j.model.chat.Capability
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import dev.langchain4j.service.AiServices
import io.ybigta.text2sql.infer.core.QuestionEntityExtractionEndpoint
import io.ybigta.text2sql.infer.core.QuestionMainClauseExtractionEndpoint
import io.ybigta.text2sql.infer.core.QuestionNormalizeEndpoint
import io.ybigta.text2sql.infer.core.logic.generate.SqlGenerationEndpoint
import io.ybigta.text2sql.infer.core.logic.table_retrieve.TblSelectionAdjustEndpoint
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import kotlin.io.path.readText

/**
 *  create and manage resource specified at infer_config.yaml.
 *
 *  limitations
 *
 *  - currently only openai model is supported
 *  - can't specifiy user prompt at ingest_config.yaml. only system prompt is allowd because of Langchain4J
 */
class InferConfig(
    val config: InferConfigFileSpec
) {
    val llmModels: Map<String, ChatModel>

    val embeddingModel: EmbeddingModel

    val pgvector: Database

    init {
        this.llmModels = config.llmModels
            .map { (modelName, apiKey) ->
                val llmModel = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                    .strictJsonSchema(true)
                    .logRequests(false)
                    .logResponses(false)
                    .build()
                Pair(modelName, llmModel)
            }
            .toMap()

        this.embeddingModel = config.embeddingModel.let { (modelName, apiKey) ->
            OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build()
        }

        this.pgvector = Database.connect(
            url = config.pgvector.jdbcUrl,
            user = config.pgvector.userName,
            password = config.pgvector.password
        )
    }

    companion object {
        fun fromConfigFile(path: Path): InferConfig = path
            .toAbsolutePath()
            .normalize()
            .readText()
            .let { Yaml.default.decodeFromString<InferConfigFileSpec>(it) }
            .let { config -> InferConfig(config) }
    }
}

object LLMEndpointBuilder {
    object QuestionTransform {
        fun buildQuestionNormalizeEndpoint(config: InferConfig): QuestionNormalizeEndpoint = AiServices
            .builder(QuestionNormalizeEndpoint::class.java)
            .chatModel(config.llmModels[config.config.llmEndPoints.questionTransform.questionNormalizeEndpoint.modelName])
            .systemMessageProvider { _ -> config.config.llmEndPoints.questionTransform.questionNormalizeEndpoint.systemPrompt }
            .build()

        fun buildQuestionMainClauseExtractionEndpoint(config: InferConfig): QuestionMainClauseExtractionEndpoint = AiServices
            .builder(QuestionMainClauseExtractionEndpoint::class.java)
            .chatModel(config.llmModels[config.config.llmEndPoints.questionTransform.questionMainClauseExtractionEndpoint.modelName])
            .systemMessageProvider { _ -> config.config.llmEndPoints.questionTransform.questionMainClauseExtractionEndpoint.systemPrompt }
            .build()

        fun buildQuestionEntityExtractionEndpoint(config: InferConfig): QuestionEntityExtractionEndpoint = AiServices
            .builder(QuestionEntityExtractionEndpoint::class.java)
            .chatModel(config.llmModels[config.config.llmEndPoints.questionTransform.questionEntityExtractionEndpoint.modelName])
            .systemMessageProvider { _ -> config.config.llmEndPoints.questionTransform.questionEntityExtractionEndpoint.systemPrompt }
            .build()
    }

    object SqlGeneration {
        fun buildQuestionEntityExtractionEndpoint(config: InferConfig): SqlGenerationEndpoint = AiServices
            .builder(SqlGenerationEndpoint::class.java)
            .chatModel(config.llmModels[config.config.llmEndPoints.sqlGeneration.sqlGenerationEndpoint.modelName])
            .systemMessageProvider { _ -> config.config.llmEndPoints.sqlGeneration.sqlGenerationEndpoint.systemPrompt }
            .build()

        fun buildTblSelectionAdjustEndpoint(config: InferConfig): TblSelectionAdjustEndpoint = AiServices
            .builder(TblSelectionAdjustEndpoint::class.java)
            .chatModel(config.llmModels[config.config.llmEndPoints.sqlGeneration.tableSelectionAdjustEndpoint.modelName])
            .systemMessageProvider { _ -> config.config.llmEndPoints.sqlGeneration.tableSelectionAdjustEndpoint.systemPrompt }
            .build()
    }
}
