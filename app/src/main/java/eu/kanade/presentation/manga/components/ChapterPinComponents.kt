package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.domain.chapter.service.PinSection
import eu.kanade.tachiyomi.ui.manga.PinFilter

/**
 * Quick filter chips: All · Bookmarked · one per section. No scrollbar; the row runs off the
 * right edge so a cut-off chip hints that it scrolls.
 */
@Composable
fun PinFilterChipRow(
    sections: List<PinSection>,
    filter: PinFilter,
    onFilterChange: (PinFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter == PinFilter.All,
            onClick = { onFilterChange(PinFilter.All) },
            label = { Text("All") },
        )
        FilterChip(
            selected = filter == PinFilter.Bookmarked,
            onClick = { onFilterChange(PinFilter.Bookmarked) },
            label = { Text("Bookmarked") },
            leadingIcon = { Icon(Icons.Filled.Bookmark, null, Modifier.size(16.dp)) },
        )
        sections.forEach { section ->
            val color = Color(section.color)
            val selected = filter == PinFilter.Section(section.id)
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(if (selected) PinFilter.All else PinFilter.Section(section.id)) },
                label = { Text(section.name) },
                leadingIcon = { Icon(Icons.Filled.Bookmark, null, Modifier.size(16.dp), tint = color) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.18f),
                    selectedLabelColor = color,
                ),
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** Collapsible header for a section's group at the top of the chapter list. */
@Composable
fun PinSectionHeader(
    section: PinSection,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(section.color)
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "caret")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Bookmark, null, Modifier.size(16.dp), tint = color)
        Text(
            text = "${section.name.uppercase()} · $count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }
}
