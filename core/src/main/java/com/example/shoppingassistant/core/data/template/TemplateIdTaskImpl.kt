package com.example.shoppingassistant.core.data.template

import com.example.shoppingassistant.domain.template.TemplateIdTask
import com.example.shoppingassistant.domain.template.TemplateSnapshotData
import java.security.MessageDigest

class TemplateIdTaskImpl : TemplateIdTask {

    override fun computeId(data: TemplateSnapshotData): String {
        val canonical = canonicalString(data)
        return sha256Hex(canonical)
    }

    private fun canonicalString(data: TemplateSnapshotData): String {
        val attrs = data.attrs
            .asSequence()
            .map { a ->
                val k = a.key.trim()
                val v = a.value.trim()
                val t = a.type.name
                Triple(k, t, v)
            }
            .sortedWith(compareBy<Triple<String, String, String>> { it.first }.thenBy { it.second }.thenBy { it.third })
            .toList()

        fun normFreeText(s: String?): String =
            s?.replace("\\s+".toRegex(), " ")?.trim().orEmpty()

        return buildString {
            append("v1|")
            append("anchorType="); append(data.anchorType.name); append('|')
            append("anchorId="); append(data.anchorId.trim()); append('|')
            append("categoryCode="); append(data.categoryCode?.trim().orEmpty()); append('|')
            append("mode="); append(data.mode.name); append('|')
            append("freeText="); append(normFreeText(data.freeText)); append('|')
            append("attrs=")
            attrs.forEachIndexed { idx, (k, t, v) ->
                if (idx > 0) append(';')
                append(k); append(':'); append(t); append('='); append(v)
            }
        }
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        val hexChars = CharArray(digest.size * 2)
        var i = 0
        for (b in digest) {
            val v = b.toInt() and 0xFF
            hexChars[i++] = "0123456789abcdef"[v ushr 4]
            hexChars[i++] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }
}

