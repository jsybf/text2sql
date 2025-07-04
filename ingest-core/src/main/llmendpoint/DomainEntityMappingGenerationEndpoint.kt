package io.ybigta.text2sql.ingest.llmendpoint

import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.ybigta.text2sql.ingest.DomainEntitiyMapping
import io.ybigta.text2sql.ingest.TableSelection

interface DomainEntityMappingGenerationEndpoint {
    /**
     * called when ingesting domain entity mapping
     */
    @UserMessage(
        """
    question: {{question}} 
    source_tables: {{tableSelections}}
    """
    )
    fun request(@V("question") question: String, @V("tableSelections") tableSelections: Set<TableSelection>): List<DomainEntitiyMapping>
}