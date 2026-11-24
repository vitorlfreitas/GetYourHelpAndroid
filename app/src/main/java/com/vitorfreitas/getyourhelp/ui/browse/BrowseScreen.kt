package com.vitorfreitas.getyourhelp.ui.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.Coordinates
import com.vitorfreitas.getyourhelp.data.TaskCategory
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.ui.components.EmptyState
import com.vitorfreitas.getyourhelp.ui.components.LoadingIndicator
import com.vitorfreitas.getyourhelp.ui.components.TaskCard
import com.vitorfreitas.getyourhelp.ui.components.icon
import com.vitorfreitas.getyourhelp.ui.components.rememberLocationAccess

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Open tasks published by other users, with a button to post a new task. Users can filter by
 * category and distance and sort by newest or nearest. With location allowed, each card shows how
 * far away the task is ("1.2 km away"). The app only asks for location when the user wants to see distances.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    tasks: List<TaskItem>,
    tasksLoaded: Boolean,
    user: AppUser,
    myLocation: Coordinates?,
    locating: Boolean,
    onRefreshLocation: (userInitiated: Boolean) -> Unit,
    onLocationDenied: () -> Unit,
    onOpenTask: (TaskItem) -> Unit,
    onCreateTask: () -> Unit
) {
    // null means "All categories" / "Any distance"
    var category by rememberSaveable { mutableStateOf<TaskCategory?>(null) }
    var maxDistanceKm by rememberSaveable { mutableStateOf<Int?>(null) }
    var sort by rememberSaveable { mutableStateOf(TaskSort.NEWEST) }
    var hideLocationHint by rememberSaveable { mutableStateOf(false) }
    val filters = BrowseFilters(category, maxDistanceKm, sort)
    val visible = remember(tasks, user.uid, filters, myLocation) { browseTasks(tasks, user.uid, filters, myLocation) }

    val locationAccess = rememberLocationAccess(onGranted = { onRefreshLocation(true) }, onDenied = onLocationDenied)
    // If location is already allowed, read it quietly so distances appear without asking.
    LaunchedEffect(locationAccess.granted) {
        if (locationAccess.granted && myLocation == null) onRefreshLocation(false)
    }
    // Sorting or filtering by distance needs the user's location, so ask for it if needed.
    fun needLocation() {
        if (myLocation == null && !locating) locationAccess.request()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Available tasks") },
                actions = {
                    IconButton(onClick = locationAccess.request, enabled = !locating) {
                        when {
                            locating -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            myLocation != null -> Icon(Icons.Filled.MyLocation, contentDescription = "Update my location")
                            else -> Icon(Icons.Outlined.LocationSearching, contentDescription = "Use my location")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateTask,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Post a task") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Hi ${user.displayName.substringBefore(' ')}! Find a task to help with, or post your own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownChip(sort, TaskSort.entries, { it.label }, Icons.AutoMirrored.Filled.Sort, sort != TaskSort.NEWEST) { option ->
                    sort = option
                    if (option == TaskSort.NEAREST) needLocation()
                }
                DropdownChip(maxDistanceKm, listOf(null) + DISTANCE_OPTIONS_KM, ::distanceLabel, Icons.Outlined.NearMe, maxDistanceKm != null) { option ->
                    maxDistanceKm = option
                    if (option != null) needLocation()
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                }
                items(TaskCategory.entries) { value ->
                    FilterChip(
                        selected = category == value,
                        // Tapping the selected category again goes back to "All"
                        onClick = { category = if (category == value) null else value },
                        label = { Text(value.label) },
                        leadingIcon = { Icon(value.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
            when {
                // The distance filter or sort is selected, but the location is not known (yet)
                filters.usesDistance && myLocation == null -> LocationBanner(
                    text = if (locating) "Finding your location…" else "Distance filters need your location. Until then, all tasks are shown.",
                    actionLabel = if (locating) null else "Turn on location",
                    onAction = locationAccess.request
                )
                // Suggest distances once, without asking for the permission straight away
                !locationAccess.granted && !hideLocationHint -> LocationBanner(
                    text = "See how far away each task is.",
                    actionLabel = "Turn on location",
                    onAction = locationAccess.request,
                    onDismiss = { hideLocationHint = true }
                )
            }
            when {
                !tasksLoaded -> LoadingIndicator()
                visible.isEmpty() -> EmptyState(
                    title = "No tasks here yet",
                    message = when {
                        maxDistanceKm != null && myLocation != null -> "No tasks within $maxDistanceKm km. Try a bigger distance."
                        category != null -> "Try another category."
                        else -> "When someone posts a task, it appears here."
                    },
                    icon = Icons.Outlined.SearchOff
                )
                else -> LazyColumn(
                    // Extra space at the bottom so the last card is not hidden behind the "Post a task" button
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visible, key = { it.task.id }) { item ->
                        TaskCard(item.task, onClick = { onOpenTask(item.task) }, distanceKm = item.distanceKm)
                    }
                }
            }
        }
    }
}

/** A chip showing the [selected] option; tapping it opens a menu with all [options] (sort, distance). */
@Composable
private fun <T> DropdownChip(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    icon: ImageVector,
    highlighted: Boolean,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = highlighted,
            onClick = { expanded = true },
            label = { Text(label(selected)) },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = { expanded = false; onSelect(option) },
                    trailingIcon = if (option == selected) { { Icon(Icons.Filled.Check, contentDescription = "Selected") } } else null
                )
            }
        }
    }
}

private fun distanceLabel(km: Int?): String = if (km == null) "Any distance" else "Within $km km"

/** A small card above the list about location, with a button and optionally a "Not now" button. */
@Composable
private fun LocationBanner(text: String, actionLabel: String?, onAction: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Not now") }
                if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
