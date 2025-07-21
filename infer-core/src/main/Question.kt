package io.ybigta.text2sql.infer.core

import io.ybigta.text2sql.infer.core.config.InferConfig
import io.ybigta.text2sql.infer.core.config.LLMEndpointBuilder
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory


class Question(
    val question: String,
    val userId: String? = null,
    private val questionNormalizeEndpoint: QuestionNormalizeEndpoint,
    private val questionMainClauseExtractionEndpoint: QuestionMainClauseExtractionEndpoint,
    private val questionEntityExtractionEndpoint: QuestionEntityExtractionEndpoint,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    val normalizedQ: Deferred<String> = GlobalScope
        .async(dispatcher) {
            questionNormalizeEndpoint
                .request(question)
                .also { logger.debug("received normalized question llm output") }
        }

    val mainClause: Deferred<String> = GlobalScope
        .async(dispatcher) {
            questionMainClauseExtractionEndpoint
                .request(question)
                .also { logger.debug("received question mainclause extraction llm output") }
        }

    val extractedEntities: Deferred<List<String>> = GlobalScope
        .async(dispatcher) {
            questionEntityExtractionEndpoint
                .request(question)
                .also { logger.debug("received question entities extraction llm output") }
        }

    companion object {
        fun fromConfig(question: String, config: InferConfig, dispatcher: CoroutineDispatcher = Dispatchers.IO,userId: String? = null) = Question(
            question,
            userId,
            LLMEndpointBuilder.QuestionTransform.buildQuestionNormalizeEndpoint(config),
            LLMEndpointBuilder.QuestionTransform.buildQuestionMainClauseExtractionEndpoint(config),
            LLMEndpointBuilder.QuestionTransform.buildQuestionEntityExtractionEndpoint(config),
            dispatcher
        )
    }
}

interface QuestionNormalizeEndpoint {
    /**
     * give question(natural language) and anwser(SQL) pair to LLM
     * get normalized question, requested entities, data source, calculations&filter
     * called when ingesting qa
     */
    fun request(question: String): String
}

interface QuestionMainClauseExtractionEndpoint {
    /**
     * give question(natural language) and anwser(SQL) pair to LLM
     * get normalized question, requested entities, data source, calculations&filter
     * called when ingesting qa
     */
    fun request(question: String): String
}

interface QuestionEntityExtractionEndpoint {
    /**
     * give question(natural language) and anwser(SQL) pair to LLM
     * get normalized question, requested entities, data source, calculations&filter
     * called when ingesting qa
     */
    fun request(question: String): List<String>
}

