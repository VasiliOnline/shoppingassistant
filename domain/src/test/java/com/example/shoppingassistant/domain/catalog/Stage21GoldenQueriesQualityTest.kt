package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class Stage21GoldenQueriesQualityTest {

    @Test
    fun duplicate_query_target_pairs_do_not_exceed_package_baseline() {
        val root = stage21RootPath()
        val packageDirs = Files.list(root).use { stream ->
            stream
                .filter { it.isDirectory() }
                .sorted()
                .toList()
        }

        val failures = mutableListOf<String>()
        packageDirs.forEach { dir ->
            val packageCode = dir.name.uppercase()
            val goldenFile = findGoldenFile(dir) ?: return@forEach
            val rows = parseTsv(goldenFile)
            if (rows.isEmpty()) return@forEach

            val headers = rows.first().keys
            val queryColumn = pickFirst(headers, "query_text", "query")
            val routeKindColumn = pickFirst(headers, "expected_route_kind", "expected_target_kind", "expected_type")
            val targetColumn = pickFirst(headers, "expected_target", "expected_code", "expected_id")
            val localeColumn = pickFirst(headers, "locale")

            if (queryColumn == null || routeKindColumn == null || targetColumn == null) {
                failures += "$packageCode: unsupported schema in ${goldenFile.fileName}"
                return@forEach
            }
            val queryColumnName = queryColumn
            val routeKindColumnName = routeKindColumn
            val targetColumnName = targetColumn

            val duplicateGroups = rows
                .groupBy { row ->
                    val locale = row[localeColumn].normalizeForKey().ifBlank { "ru-ru" }
                    val query = row[queryColumnName].normalizeForKey()
                    val routeKind = row[routeKindColumnName].normalizeForKey()
                    val target = row[targetColumnName].normalizeUpperForKey()
                    "$locale|$query|$routeKind|$target"
                }
                .filterValues { it.size > 1 }

            val duplicatePairs = duplicateGroups.size
            val allowed = duplicatePairsBaselineByPackage[packageCode] ?: 0
            if (duplicatePairs > allowed) {
                val topSamples = duplicateGroups
                    .entries
                    .sortedByDescending { it.value.size }
                    .take(5)
                    .joinToString("; ") { (key, value) ->
                        val sample = value.first()[queryColumnName].orEmpty().trim()
                        "$sample x${value.size} [$key]"
                    }
                failures += "$packageCode: duplicate pairs=$duplicatePairs (allowed=$allowed). Top: $topSamples"
            }
        }

        assertTrue(
            buildString {
                append("Golden queries duplicate guard failed. ")
                append("Fix datasets or update baseline intentionally. ")
                append("Issues: ")
                append(failures.joinToString(" | "))
            },
            failures.isEmpty(),
        )
    }

    private fun stage21RootPath(): Path {
        val candidate = Paths.get("src/main/resources/taxonomy/stage2/2.1")
        require(Files.exists(candidate) && Files.isDirectory(candidate)) {
            "Stage 2.1 resources folder not found: $candidate"
        }
        return candidate
    }

    private fun findGoldenFile(packageDir: Path): Path? =
        Files.list(packageDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().startsWith("queries_golden.") && it.fileName.toString().endsWith(".tsv") }
                .findFirst()
                .orElse(null)
        }

    private fun parseTsv(path: Path): List<Map<String, String>> {
        val lines = Files.readAllLines(path, StandardCharsets.UTF_8)
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val headers = lines.first().split('\t').map { it.trim() }.toMutableList()
        if (headers.isEmpty()) return emptyList()
        headers[0] = headers[0].removePrefix("\uFEFF")

        return lines.drop(1).map { line ->
            val cells = line.split('\t')
            headers.indices.associate { index ->
                headers[index] to cells.getOrElse(index) { "" }
            }
        }
    }

    private fun pickFirst(headers: Set<String>, vararg candidates: String): String? =
        candidates.firstOrNull { it in headers }

    private fun String?.normalizeForKey(): String = this
        .orEmpty()
        .trim()
        .lowercase()
        .replace('ё', 'е')
        .replace("\\s+".toRegex(), " ")

    private fun String?.normalizeUpperForKey(): String = this
        .orEmpty()
        .trim()
        .uppercase()
        .replace("\\s+".toRegex(), " ")

    private companion object {
        // Non-regression baseline. Reduce numbers over time; do not increase without dataset review.
        val duplicatePairsBaselineByPackage: Map<String, Int> = mapOf(
            "APPL" to 0,
            "AUTO" to 0,
            "BEAUTY" to 0,
            "FASH" to 0,
            "FOOD" to 0,
            "HOME" to 0,
            "KIDS" to 0,
            "PETS" to 0,
            "SPORT" to 0,
            "TECH" to 0,
        )
    }
}
