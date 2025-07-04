package io.ybigta.text2sql.exposed.pgvector

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.wrap

/**
 * implement pgvector operators
 */

class CosineDistanceOp<T : Number>(
    left: Expression<FloatArray>,
    right: Expression<FloatArray>,
    columnType: IColumnType<T>
) : CustomOperator<T>("<=>", columnType, left, right)

infix fun ExpressionWithColumnType<FloatArray>.cosDist(t: FloatArray) = CosineDistanceOp(this, wrap(t), FloatColumnType())
