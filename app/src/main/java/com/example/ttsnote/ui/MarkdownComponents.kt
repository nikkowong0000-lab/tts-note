package com.example.ttsnote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownTable(
    rows: List<List<String>>,
    baseFontSize: Int
) {
    if (rows.isEmpty()) return
    
    val scrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .horizontalScroll(scrollState)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (rowIndex == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .padding(vertical = 8.dp)
                ) {
                    row.forEach { cell ->
                        Text(
                            text = parseInlineMarkdown(cell),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = (baseFontSize - 2).sp
                            ),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .widthIn(min = 100.dp, max = 300.dp)
                        )
                    }
                }
                if (rowIndex < rows.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun MarkdownDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        thickness = 2.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun MarkdownQuote(content: String, baseFontSize: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = parseInlineMarkdown(content),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = baseFontSize.sp,
                lineHeight = (baseFontSize + 8).sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        )
    }
}

@Composable
fun MarkdownCallout(content: String, baseFontSize: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lightbulb, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = parseInlineMarkdown(content),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (baseFontSize - 1).sp,
                    lineHeight = (baseFontSize + 6).sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
