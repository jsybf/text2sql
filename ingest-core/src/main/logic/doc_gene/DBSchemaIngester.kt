package io.ybigta.text2sql.ingest.logic.doc_gene

import io.ybigta.text2sql.ingest.TableName
import io.ybigta.text2sql.ingest.TableSchema
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.ResultSet

/**
 * query table schemas(information) from database.
 * currently only postrgres is only supported DB.
 */
internal sealed interface DBSchemaIngester {
    fun requestTableSchemas(): List<TableSchema>
}

internal class PostgresSchemaIngester(
    private val db: Database
) : DBSchemaIngester {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun requestTableSchemas(): List<TableSchema> = transaction(db) {
        logger.info("querying postgres for list of tables")

        val tables: List<TableName> = """
        SELECT table_schema, table_name
        FROM information_schema.tables
        WHERE table_type = 'BASE TABLE' AND table_schema NOT IN ('pg_catalog', 'information_schema')
        ;
        """.execSql(this) { rs -> TableName(rs.getString(1), rs.getString(2)) }


        return@transaction tables.map { (schemaName, tableName) ->
            logger.debug("requesting postgresql for table defintion(tableName={} schemaName={})", tableName, schemaName)
            val columnsInfo: List<Map<String, String?>> = """
			SELECT
			    c.table_schema,
			    c.table_name,
			    obj_description(pgc.oid) as table_comment,
			    c.column_name,
			    col_description(pgc.oid, c.ordinal_position) as column_comment,
			    c.data_type,
			    c.is_nullable,
			    c.column_default,
			    CASE
			        WHEN tc.constraint_type = 'PRIMARY KEY' THEN 'PK'
			        WHEN tc.constraint_type = 'UNIQUE' THEN 'UK'
			        WHEN tc.constraint_type = 'FOREIGN KEY' THEN 'FK'
			        ELSE ''
			        END as constraint_info,
			    CASE
			        WHEN tc.constraint_type = 'FOREIGN KEY' THEN
			            (SELECT ccu.table_schema || '.' || ccu.table_name || '(' || ccu.column_name || ')'
			             FROM information_schema.constraint_column_usage ccu
			             WHERE ccu.constraint_name = tc.constraint_name)
			        ELSE ''
			        END as fk_reference
			FROM
			    information_schema.columns c
			    JOIN pg_class pgc ON pgc.relname = c.table_name AND pgc.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = c.table_schema)
			    LEFT JOIN information_schema.key_column_usage kcu ON kcu.column_name = c.column_name AND kcu.table_name = c.table_name AND kcu.table_schema = c.table_schema
			    LEFT JOIN information_schema.table_constraints tc ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
			WHERE c.table_schema = '${schemaName}'
			  AND c.table_name = '${tableName}'
			ORDER BY c.ordinal_position;
            """.execSql(this) { rs ->
                mapOf(
                    "table_schema" to rs.getStringOrNull(1),
                    "table_name" to rs.getStringOrNull(2),
                    "table_comment" to rs.getStringOrNull(3),
                    "column_name" to rs.getStringOrNull(4),
                    "column_comment" to rs.getStringOrNull(5),
                    "data_type" to rs.getStringOrNull(6),
                    "is_nullable" to rs.getStringOrNull(7),
                    "column_default" to rs.getStringOrNull(8),
                    "constraint_info" to rs.getStringOrNull(9),
                    "fk_reference" to rs.getStringOrNull(10),
                )
            }
            TableSchema(TableName(schemaName, tableName), columnsInfo)
        }
    }

}

/**
 * below is util functions for jdbc and exposed
 * util function for execute raw sql string and map it's ResultSet
 */
private fun <T> String.execSql(transaction: Transaction, fn: (ResultSet) -> T): List<T> = transaction.exec(this) { rs -> rs.map(fn).toList() }!!


private fun <T> ResultSet.map(fn: (ResultSet) -> T): Iterable<T> {
    val rs = this

    val iterator = object : Iterator<T> {
        override fun hasNext(): Boolean = rs.next()

        override fun next(): T = fn(rs)
    }

    return object : Iterable<T> {
        override fun iterator(): Iterator<T> = iterator
    }
}

/**
 * NOTE: getXxx of JDBC could returns NULL. so wrapping it for null safety
 */
private fun ResultSet.getStringOrNull(columnName: String): String? {
    return wasNull(this, getString(columnName))
}

private fun ResultSet.getStringOrNull(columnIdx: Int): String? {
    return wasNull(this, getString(columnIdx))
}

private fun ResultSet.getString(columnName: String): String {
    return this.getString(columnName)
}

private fun ResultSet.getString(columnIdx: Int): String {
    return this.getString(columnIdx)
}

private fun <T> wasNull(resultSet: ResultSet, value: T) = when {
    resultSet.wasNull() -> null
    else -> value
}
