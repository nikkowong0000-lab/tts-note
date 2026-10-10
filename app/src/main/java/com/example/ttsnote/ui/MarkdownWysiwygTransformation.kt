package com.example.ttsnote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

class MarkdownWysiwygTransformation(
    private val highlightRange: Pair<Int, Int>?,
    private val highlightColor: Color,
    private val headerColor: Color,
    private val hideSymbols: Boolean = true,
    private val baseFontSize: Int = 18,
    private val searchQuery: String = ""
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val originalToTransformed = IntArray(originalText.length + 1)
        val transformedToOriginal = mutableListOf<Int>()

        val annotatedString = buildAnnotatedString {
            var originalIndex = 0
            val lines = originalText.split("\n")
            
            lines.forEachIndexed { lineIndex, line ->
                val lineStartInOriginal = originalIndex
                val isHeader = line.trimStart().startsWith("#")
                val isQuote = line.trimStart().startsWith(">")
                val isList = line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")
                val isAside = line.contains("<aside>") || line.contains("</aside>")

                if (!hideSymbols) {
                    appendWithMapping(line, lineStartInOriginal, originalToTransformed, transformedToOriginal)
                } else {
                    when {
                        isHeader -> {
                            val headerLevel = line.takeWhile { it == '#' }.length
                            val headerContent = line.substring(headerLevel).trim()
                            for (i in 0 until headerLevel) originalToTransformed[lineStartInOriginal + i] = length
                            val leadingSpaceCount = line.substring(headerLevel).takeWhile { it.isWhitespace() }.length
                            for (i in 0 until leadingSpaceCount) originalToTransformed[lineStartInOriginal + headerLevel + i] = length

                            withStyle(SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (headerLevel == 1) (baseFontSize + 12).sp else (baseFontSize + 6).sp,
                                color = headerColor
                            )) {
                                appendWithMapping(headerContent, lineStartInOriginal + headerLevel + leadingSpaceCount, originalToTransformed, transformedToOriginal)
                            }
                            for (i in (headerLevel + leadingSpaceCount + headerContent.length) until line.length) originalToTransformed[lineStartInOriginal + i] = length
                        }
                        isQuote -> {
                            val quoteContent = line.substring(1).trim()
                            for (i in 0 until 1) originalToTransformed[lineStartInOriginal + i] = length
                            withStyle(SpanStyle(background = Color.LightGray.copy(alpha = 0.2f), fontSize = (baseFontSize + 2).sp)) {
                                appendWithMapping(quoteContent, lineStartInOriginal + 1, originalToTransformed, transformedToOriginal)
                            }
                            for (i in (1 + quoteContent.length) until line.length) originalToTransformed[lineStartInOriginal + i] = length
                        }
                        isList -> {
                            val listContent = line.substring(2).trim()
                            for (i in 0 until 2) originalToTransformed[lineStartInOriginal + i] = length
                            append("• ")
                            transformedToOriginal.add(lineStartInOriginal)
                            transformedToOriginal.add(lineStartInOriginal + 1)
                            appendWithMapping(listContent, lineStartInOriginal + 2, originalToTransformed, transformedToOriginal)
                            for (i in (2 + listContent.length) until line.length) originalToTransformed[lineStartInOriginal + i] = length
                        }
                        isAside -> {
                            val cleanLine = line.replace("<aside>", "").replace("</aside>", "").trim()
                            withStyle(SpanStyle(background = Color.Gray.copy(alpha = 0.1f), fontStyle = FontStyle.Italic)) {
                                appendWithMapping(cleanLine, lineStartInOriginal, originalToTransformed, transformedToOriginal)
                            }
                            // Map hidden tags
                            for (i in 0 until line.length) {
                                if (originalToTransformed[lineStartInOriginal + i] == 0 && lineStartInOriginal + i < originalToTransformed.size - 1) {
                                    originalToTransformed[lineStartInOriginal + i] = length
                                }
                            }
                        }
                        else -> {
                            appendWithMapping(line, lineStartInOriginal, originalToTransformed, transformedToOriginal)
                        }
                    }
                }

                originalIndex += line.length
                if (lineIndex < lines.size - 1) {
                    originalToTransformed[originalIndex] = length
                    append("\n")
                    transformedToOriginal.add(originalIndex)
                    originalIndex += 1
                }
            }
            originalToTransformed[originalText.length] = length
        }

        // Apply Highlights (Sentence and Search)
        val finalAnnotatedString = buildAnnotatedString {
            append(annotatedString)
            
            // Search Query Highlight
            if (searchQuery.isNotBlank()) {
                var index = originalText.indexOf(searchQuery, ignoreCase = true)
                while (index != -1) {
                    val tStart = originalToTransformed[index]
                    val tEnd = originalToTransformed[index + searchQuery.length]
                    if (tStart < tEnd) {
                        addStyle(SpanStyle(background = Color(0xFFFFF176)), tStart, tEnd) // Soft Yellow
                    }
                    index = originalText.indexOf(searchQuery, index + 1, ignoreCase = true)
                }
            }

            // Sentence Highlight
            highlightRange?.let { range ->
                val start = range.first.coerceIn(0, originalText.length)
                val end = range.second.coerceIn(0, originalText.length)
                val tStart = originalToTransformed[start]
                val tEnd = originalToTransformed[end]
                if (tStart < tEnd) {
                    addStyle(SpanStyle(background = highlightColor), tStart, tEnd)
                }
            }
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = originalToTransformed[offset.coerceIn(0, originalText.length)]
            override fun transformedToOriginal(offset: Int): Int = if (offset < transformedToOriginal.size) transformedToOriginal[offset] else originalText.length
        }

        return TransformedText(finalAnnotatedString, mapping)
    }

    private fun AnnotatedString.Builder.appendWithMapping(text: String, startInOriginal: Int, originalToTransformed: IntArray, transformedToOriginal: MutableList<Int>) {
        var i = 0
        while (i < text.length) {
            if (hideSymbols && i < text.length - 2 && text.substring(i, i + 2) == "**") {
                originalToTransformed[startInOriginal + i] = length
                originalToTransformed[startInOriginal + i + 1] = length
                i += 2
                val nextBold = text.indexOf("**", i)
                val boldContent = if (nextBold != -1) text.substring(i, nextBold) else text.substring(i)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    boldContent.forEachIndexed { index, char ->
                        originalToTransformed[startInOriginal + i + index] = length
                        transformedToOriginal.add(startInOriginal + i + index)
                        append(char)
                    }
                }
                i += boldContent.length
                if (nextBold != -1) {
                    originalToTransformed[startInOriginal + i] = length
                    originalToTransformed[startInOriginal + i + 1] = length
                    i += 2
                }
            } else {
                originalToTransformed[startInOriginal + i] = length
                transformedToOriginal.add(startInOriginal + i)
                append(text[i])
                i++
            }
        }
    }
}
