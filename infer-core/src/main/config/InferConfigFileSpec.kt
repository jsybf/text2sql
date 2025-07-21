package io.ybigta.text2sql.infer.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InferConfigFileSpec(
    @SerialName("PgVector")
    val pgvector: PgVectorSpec,
    @SerialName("EmbeddingModel")
    val embeddingModel: EmbeddingModelSpec,
    @SerialName("LLMModels")
    val llmModels: List<LLMModelSpec>,
    @SerialName("LLMEndPoints")
    val llmEndPoints: LLMEndPointsSpec
) {
    @Serializable
    data class EmbeddingModelSpec(
        val modelName: String,
        val apiKey: String
    )

    @Serializable
    data class LLMModelSpec(
        val modelName: String,
        val apiKey: String
    )

    @Serializable
    data class PgVectorSpec(
        val jdbcUrl: String,
        val userName: String,
        val password: String
    )

    @Serializable
    data class LLMEndPointsSpec(
        @SerialName("QuestionTransform")
        val questionTransform: QuestionTransformSpec,
        @SerialName("SqlGeneration")
        val sqlGeneration: SqlGenerationSpec,
    ) {
        @Serializable
        data class LLMEndPointSpec(
            val modelName: String,
            val systemPrompt: String
        )

        @Serializable
        data class QuestionTransformSpec(
            @SerialName("QuestionNormalizeEndpoint")
            val questionNormalizeEndpoint: LLMEndPointSpec,
            @SerialName("QuestionMainClauseExtractionEndpoint")
            val questionMainClauseExtractionEndpoint: LLMEndPointSpec,
            @SerialName("QuestionEntityExtractionEndpoint")
            val questionEntityExtractionEndpoint: LLMEndPointSpec
        )

        @Serializable
        data class SqlGenerationSpec(
            @SerialName("TableSelectionAdjustEndpoint")
            val tableSelectionAdjustEndpoint: LLMEndPointSpec,
            @SerialName("SqlGenerationEndpoint")
            val sqlGenerationEndpoint: LLMEndPointSpec
        )
    }
}
