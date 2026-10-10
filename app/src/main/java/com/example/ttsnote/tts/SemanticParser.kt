package com.example.ttsnote.tts

enum class BlockType {
    Header, Quote, List, Paragraph, Table, Divider, CodeBlock, Callout
}

data class SemanticBlock(
    val type: BlockType,
    val content: String,
    val startIndex: Int,
    val endIndex: Int,
    val level: Int = 0, // For headers
    val rows: List<List<String>> = emptyList() // For tables
)

object SemanticParser {
    fun parse(text: String): List<SemanticBlock> {
        val blocks = mutableListOf<SemanticBlock>()
        val lines = text.split("\n")
        var currentIndex = 0
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trim()
            val lineLength = line.length + 1
            
            when {
                trimmedLine.startsWith("#") -> {
                    val headerText = line.trimStart()
                    val level = headerText.takeWhile { it == '#' }.length
                    blocks.add(SemanticBlock(
                        type = BlockType.Header,
                        content = headerText.substring(level).trim(),
                        startIndex = currentIndex,
                        endIndex = currentIndex + line.length,
                        level = level
                    ))
                    i++
                    currentIndex += lineLength
                }
                trimmedLine.startsWith(">") -> {
                    val start = currentIndex
                    val content = StringBuilder()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        content.append(lines[i].trim().substring(1).trim()).append("\n")
                        currentIndex += lines[i].length + 1
                        i++
                    }
                    blocks.add(SemanticBlock(
                        type = BlockType.Quote,
                        content = content.toString().trim(),
                        startIndex = start,
                        endIndex = currentIndex - 1
                    ))
                }
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || trimmedLine.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val start = currentIndex
                    val content = StringBuilder()
                    while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* ") || lines[i].trim().matches(Regex("^\\d+\\.\\s.*")))) {
                        content.append(lines[i].trim()).append("\n")
                        currentIndex += lines[i].length + 1
                        i++
                    }
                    blocks.add(SemanticBlock(
                        type = BlockType.List,
                        content = content.toString().trim(),
                        startIndex = start,
                        endIndex = currentIndex - 1
                    ))
                }
                trimmedLine == "---" || trimmedLine == "***" -> {
                    blocks.add(SemanticBlock(
                        type = BlockType.Divider,
                        content = line,
                        startIndex = currentIndex,
                        endIndex = currentIndex + line.length
                    ))
                    i++
                    currentIndex += lineLength
                }
                trimmedLine.startsWith("<aside>") -> {
                    val start = currentIndex
                    val content = StringBuilder()
                    // Handle multi-line aside
                    while (i < lines.size) {
                        val currentLine = lines[i]
                        val cleanLine = currentLine.replace("<aside>", "").replace("</aside>", "")
                        content.append(cleanLine).append("\n")
                        currentIndex += currentLine.length + 1
                        i++
                        if (currentLine.contains("</aside>")) break
                    }
                    blocks.add(SemanticBlock(
                        type = BlockType.Callout,
                        content = content.toString().trim(),
                        startIndex = start,
                        endIndex = currentIndex - 1
                    ))
                }
                trimmedLine.startsWith("|") && i + 1 < lines.size && lines[i+1].contains("|") && lines[i+1].contains("-") -> {
                    // Table detection
                    val start = currentIndex
                    val tableRows = mutableListOf<List<String>>()
                    
                    // Header
                    tableRows.add(parseTableRow(lines[i]))
                    currentIndex += lines[i].length + 1
                    i++
                    
                    // Separator line (skip visually but count index)
                    currentIndex += lines[i].length + 1
                    i++
                    
                    // Body
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        tableRows.add(parseTableRow(lines[i]))
                        currentIndex += lines[i].length + 1
                        i++
                    }
                    
                    blocks.add(SemanticBlock(
                        type = BlockType.Table,
                        content = "", // Table uses 'rows'
                        startIndex = start,
                        endIndex = currentIndex - 1,
                        rows = tableRows
                    ))
                }
                trimmedLine.isEmpty() -> {
                    i++
                    currentIndex += lineLength
                }
                else -> {
                    val start = currentIndex
                    val content = StringBuilder()
                    while (i < lines.size && lines[i].trim().isNotEmpty() && 
                           !lines[i].trim().startsWith("#") && 
                           !lines[i].trim().startsWith(">") && 
                           !lines[i].trim().startsWith("- ") && 
                           !lines[i].trim().startsWith("|")) {
                        content.append(lines[i]).append("\n")
                        currentIndex += lines[i].length + 1
                        i++
                    }
                    blocks.add(SemanticBlock(
                        type = BlockType.Paragraph,
                        content = content.toString().trim(),
                        startIndex = start,
                        endIndex = currentIndex - 1
                    ))
                }
            }
        }
        return blocks
    }

    private fun parseTableRow(line: String): List<String> {
        return line.trim().removeSurrounding("|").split("|").map { it.trim() }
    }
    
    fun findTitle(text: String): String? {
        return text.split("\n").firstOrNull { it.trim().startsWith("#") }?.trim()?.removePrefix("#")?.trim()
    }
}
