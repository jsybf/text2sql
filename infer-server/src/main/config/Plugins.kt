package io.ybigta.text2sql.infer.server.config

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun Application.pluginConfig() {
    install(ContentNegotiation) {
        json(Json { prettyPrint = true })
    }
}
