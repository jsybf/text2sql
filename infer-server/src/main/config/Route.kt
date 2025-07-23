package io.ybigta.text2sql.infer.server.config

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ybigta.text2sql.infer.server.InferService
import io.ybigta.text2sql.infer.core.InferResp 
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json 

@Serializable
internal data class InferRequest(
    val userId: String,
    val question: String
)

@Serializable
internal data class BatchInferRequest(
    val userId: String,
    val questions: List<String>
)

@Serializable
internal data class BatchResponse(
    val results: List<InferResp>
)


internal fun Application.routeConfig(
    scope: CoroutineScope,
    inferService: InferService,
) {
    routing {
        post("/infer") {
            val inferRequest = call.receive<InferRequest>()
            val inferResp = scope.async { inferService.infer(inferRequest.question) }
            call.respond(inferResp.await())
        }
        post("/infer-batch") {
            val batchRequest = call.receive<BatchInferRequest>()

            val deferredResults = batchRequest.questions.map { question ->
                scope.async { inferService.infer(question) }
            }

            val results = deferredResults.map { it.await() }

            call.respond(BatchResponse(results))
        }
    }
}