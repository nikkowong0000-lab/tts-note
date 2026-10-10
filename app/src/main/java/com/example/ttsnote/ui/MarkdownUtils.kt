package com.example.ttsnote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

fun stripMarkdown(text: String): String {
    return text
        .replace("**", "")
        .replace("*", "")
        .replace("`", "")
        .trim()
}

fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold: **text**
                i < text.length - 2 && text.substring(i, i + 2) == "**" -> {
                    i += 2
                    val nextBold = text.indexOf("**", i)
                    if (nextBold != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i, nextBold))
                        }
                        i = nextBold + 2
                    } else {
                        append("**")
                    }
                }
                // Italic: *text* (avoid matching ** or inside **)
                i < text.length - 1 && text[i] == '*' && text[i+1] != '*' -> {
                    i += 1
                    val nextItalic = text.indexOf('*', i)
                    if (nextItalic != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i, nextItalic))
                        }
                        i = nextItalic + 1
                    } else {
                        append("*")
                    }
                }
                // Inline Code: `text`
                text[i] == '`' -> {
                    i += 1
                    val nextCode = text.indexOf('`', i)
                    if (nextCode != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.LightGray.copy(alpha = 0.3f)
                        )) {
                            append(text.substring(i, nextCode))
                        }
                        i = nextCode + 1
                    } else {
                        append("`")
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
