package com.llamaagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Lekki renderer prostego Markdown:
 * - bloki kodu ```...``` (monospace, tło)
 * - **pogrubienie**
 * - `kod inline`
 */
@Composable
fun MarkdownText(text: String, color: Color) {
    val segments = remember(text) { splitCodeBlocks(text) }
    Column {
        for (seg in segments) {
            if (seg.isCode) {
                Text(
                    text = seg.text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.background,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(8.dp)
                )
            } else {
                Text(
                    text = renderInline(seg.text),
                    color = color,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private data class Segment(val text: String, val isCode: Boolean)

private fun splitCodeBlocks(text: String): List<Segment> {
    val result = mutableListOf<Segment>()
    val parts = text.split("```")
    for ((i, part) in parts.withIndex()) {
        if (part.isEmpty()) continue
        val isCode = (i % 2 == 1)
        val cleaned = if (isCode) part.substringAfter('\n', part).trimEnd() else part
        result.add(Segment(cleaned, isCode))
    }
    if (result.isEmpty()) result.add(Segment(text, false))
    return result
}

private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Color(0xFF9DCBFF))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
