package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.model.TypedAttributeFilter
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.asDoubleOrNull
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.DoubleColumnType
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.QueryParameter
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.or

class TypedAttributeQueryCompiler(
    private val stage4ExecutionLayer: Stage4ExecutionLayer,
) {
    @Suppress("UNCHECKED_CAST")
    fun containsTyped(
        column: Column<*>,
        attributes: Map<String, TypedAttributeValue>,
    ): Op<Boolean> {
        val param = QueryParameter(
            attributes,
            column.columnType as IColumnType<Map<String, TypedAttributeValue>>,
        )
        return (column as ExpressionWithColumnType<Any>).contains(param)
    }

    fun containsRaw(
        column: Column<*>,
        attributes: Map<String, String>,
    ): Op<Boolean> = containsTyped(
        column = column,
        attributes = stage4ExecutionLayer.toTypedAttributes(attributes),
    )

    fun compileAcrossColumns(
        primaryColumn: Column<*>,
        secondaryColumn: Column<*>,
        filters: Map<String, TypedAttributeFilter>,
    ): Op<Boolean>? {
        if (filters.isEmpty()) return null
        val perAttributeOps = filters.mapNotNull { (attributeCode, filter) ->
            val primary = compileFilter(primaryColumn, attributeCode, filter)
            val secondary = compileFilter(secondaryColumn, attributeCode, filter)
            when {
                primary != null && secondary != null -> primary or secondary
                primary != null -> primary
                else -> secondary
            }
        }
        return perAttributeOps.reduceOrNull { acc, op -> acc and op }
    }

    fun compileFilter(
        column: Column<*>,
        attributeCode: String,
        filter: TypedAttributeFilter,
    ): Op<Boolean>? {
        val normalizedAttributeCode = attributeCode.trim()
        if (normalizedAttributeCode.isEmpty()) return null

        return when (filter.op) {
            TypedAttributeOperator.EQ -> {
                val value = singleValue(filter) ?: return null
                containsTyped(column, mapOf(normalizedAttributeCode to value))
            }
            TypedAttributeOperator.NEQ -> {
                val value = singleValue(filter) ?: return null
                keyExists(column, normalizedAttributeCode) and
                    not(containsTyped(column, mapOf(normalizedAttributeCode to value)))
            }
            TypedAttributeOperator.GT -> {
                val value = singleValue(filter)?.asDoubleOrNull() ?: return null
                numericCompare(column, normalizedAttributeCode, ">", value)
            }
            TypedAttributeOperator.GTE -> {
                val value = singleValue(filter)?.asDoubleOrNull() ?: return null
                numericCompare(column, normalizedAttributeCode, ">=", value)
            }
            TypedAttributeOperator.LT -> {
                val value = singleValue(filter)?.asDoubleOrNull() ?: return null
                numericCompare(column, normalizedAttributeCode, "<", value)
            }
            TypedAttributeOperator.LTE -> {
                val value = singleValue(filter)?.asDoubleOrNull() ?: return null
                numericCompare(column, normalizedAttributeCode, "<=", value)
            }
            TypedAttributeOperator.BETWEEN -> {
                val from = filter.from?.asDoubleOrNull()
                val to = filter.to?.asDoubleOrNull()
                when {
                    from != null && to != null ->
                        numericCompare(column, normalizedAttributeCode, ">=", from) and
                            numericCompare(column, normalizedAttributeCode, "<=", to)
                    from != null -> numericCompare(column, normalizedAttributeCode, ">=", from)
                    to != null -> numericCompare(column, normalizedAttributeCode, "<=", to)
                    else -> null
                }
            }
            TypedAttributeOperator.IN -> {
                val values = values(filter)
                if (values.isEmpty()) return null
                values
                    .map { value ->
                        containsTyped(column, mapOf(normalizedAttributeCode to value))
                    }
                    .reduceOrNull { acc, op -> acc or op }
            }
            TypedAttributeOperator.CONTAINS -> {
                val query = singleValue(filter)?.asRawString()?.trim()
                if (query.isNullOrEmpty()) return null
                textContains(column, normalizedAttributeCode, query)
            }
            TypedAttributeOperator.EXISTS -> keyExists(column, normalizedAttributeCode)
            TypedAttributeOperator.NOT_EXISTS -> not(keyExists(column, normalizedAttributeCode))
        }
    }

    private fun singleValue(filter: TypedAttributeFilter): TypedAttributeValue? =
        filter.value ?: filter.values.firstOrNull()

    private fun values(filter: TypedAttributeFilter): List<TypedAttributeValue> {
        val collected = buildList {
            filter.value?.let { add(it) }
            addAll(filter.values)
        }
        return collected.distinct()
    }

    private fun keyExists(
        column: Column<*>,
        attributeCode: String,
    ): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append("jsonb_exists(")
            queryBuilder.append(column)
            queryBuilder.append(", ")
            queryBuilder.registerArgument(TextColumnType(), attributeCode)
            queryBuilder.append(")")
        }
    }

    private fun textContains(
        column: Column<*>,
        attributeCode: String,
        query: String,
    ): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append("LOWER(COALESCE(")
            queryBuilder.append(column)
            queryBuilder.append("->>")
            queryBuilder.registerArgument(TextColumnType(), attributeCode)
            queryBuilder.append(", '')) LIKE ")
            queryBuilder.registerArgument(TextColumnType(), "%${query.lowercase()}%")
        }
    }

    private fun numericCompare(
        column: Column<*>,
        attributeCode: String,
        operator: String,
        value: Double,
    ): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append("(")
            appendNumericValueExpr(queryBuilder, column, attributeCode)
            queryBuilder.append(" IS NOT NULL AND ")
            appendNumericValueExpr(queryBuilder, column, attributeCode)
            queryBuilder.append(" ")
            queryBuilder.append(operator)
            queryBuilder.append(" ")
            queryBuilder.registerArgument(DoubleColumnType(), value)
            queryBuilder.append(")")
        }
    }

    private fun appendNumericValueExpr(
        queryBuilder: QueryBuilder,
        column: Column<*>,
        attributeCode: String,
    ) {
        queryBuilder.append("CASE WHEN COALESCE(")
        queryBuilder.append(column)
        queryBuilder.append("->>")
        queryBuilder.registerArgument(TextColumnType(), attributeCode)
        queryBuilder.append(", '') ~ '^-?[0-9]+(?:[\\.,][0-9]+)?$' THEN REPLACE(")
        queryBuilder.append(column)
        queryBuilder.append("->>")
        queryBuilder.registerArgument(TextColumnType(), attributeCode)
        queryBuilder.append(", ',', '.')::double precision ELSE NULL END")
    }
}
