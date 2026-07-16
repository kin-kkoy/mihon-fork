package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalSearchToolbar(
    searchQuery: String?,
    progress: Int,
    total: Int,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    hideSourceFilter: Boolean,
    enabledSources: List<Source>,
    selectedSourceIds: Set<Long>,
    onToggleSource: (Source) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onlyShowHasResults: Boolean,
    onToggleResults: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Box {
            SearchToolbar(
                searchQuery = searchQuery,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                onClickCloseSearch = navigateUp,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (!hideSourceFilter) {
                        SourcePickerButton(
                            enabledSources = enabledSources,
                            selectedSourceIds = selectedSourceIds,
                            onToggleSource = onToggleSource,
                            onSelectAll = onSelectAll,
                            onSelectNone = onSelectNone,
                        )
                    }
                },
            )
            if (progress in 1..<total) {
                LinearProgressIndicator(
                    progress = { progress / total.toFloat() },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            FilterChip(
                selected = onlyShowHasResults,
                onClick = { onToggleResults() },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = null,
                        modifier = Modifier
                            .size(FilterChipDefaults.IconSize),
                    )
                },
                label = {
                    Text(text = stringResource(MR.strings.has_results))
                },
            )
        }

        HorizontalDivider()
    }
}

@Composable
private fun SourcePickerButton(
    enabledSources: List<Source>,
    selectedSourceIds: Set<Long>,
    onToggleSource: (Source) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        BadgedBox(
            badge = {
                if (selectedSourceIds.isNotEmpty()) {
                    Badge {
                        Text(text = "${selectedSourceIds.size}")
                    }
                }
            },
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Search in",
                )
            }
        }

        SourcePickerMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            enabledSources = enabledSources,
            selectedSourceIds = selectedSourceIds,
            onToggleSource = onToggleSource,
            onSelectAll = onSelectAll,
            onSelectNone = onSelectNone,
        )
    }
}

@Composable
private fun SourcePickerMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    enabledSources: List<Source>,
    selectedSourceIds: Set<Long>,
    onToggleSource: (Source) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var filterText by rememberSaveable { mutableStateOf("") }

    val filteredSources = remember(enabledSources, filterText) {
        if (filterText.isBlank()) {
            enabledSources
        } else {
            enabledSources.filter { it.name.contains(filterText, ignoreCase = true) }
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(320.dp),
    ) {
        Text(
            text = "Search in",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        )

        if (searchExpanded) {
            TextField(
                value = filterText,
                onValueChange = { filterText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.small),
                placeholder = { Text(text = "Filter sources") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            filterText = ""
                            searchExpanded = false
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_reset),
                        )
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selectedSourceIds.size} of ${enabledSources.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll) {
                    Text(text = "Select all")
                }
                TextButton(onClick = onSelectNone) {
                    Text(text = "None")
                }
                IconButton(onClick = { searchExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(MR.strings.action_search),
                    )
                }
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            filteredSources.forEach { source ->
                val checked = source.id in selectedSourceIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSource(source) }
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    SourceIcon(
                        source = source,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = LocaleHelper.getLocalizedDisplayName(source.lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggleSource(source) },
                    )
                }
            }
        }
    }
}
