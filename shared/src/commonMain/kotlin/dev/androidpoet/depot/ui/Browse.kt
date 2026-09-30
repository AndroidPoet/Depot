package dev.androidpoet.depot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.androidpoet.depot.Brand
import dev.androidpoet.depot.catalog.CatalogApp
import dev.androidpoet.depot.catalog.SortOrder
import dev.androidpoet.depot.engine.DeviceState
import dev.androidpoet.depot.engine.InstallState
import dev.androidpoet.depot.engine.SyncState
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

@Composable
fun Sidebar(state: HomeState, viewModel: DepotViewModel, modifier: Modifier = Modifier) {
    Column(modifier.background(Ui.colors.canvas)) {
        Row(Modifier.padding(start = 20.dp, top = 22.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.Crate, Ui.colors.accent, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Label(Brand.NAME, Ui.type.title.copy(fontSize = Ui.type.title.fontSize * 0.82f))
        }
        Shelf.entries.forEach { shelf ->
            val count = when (shelf) {
                Shelf.All -> state.listing.total
                Shelf.Installed -> state.listing.installedCount
                Shelf.Updates -> state.listing.updateCount
            }
            SidebarRow(
                label = shelf.label,
                count = count,
                selected = state.shelf == shelf,
                emphasised = shelf == Shelf.Updates && count > 0,
                onClick = { viewModel.showShelf(shelf) },
            )
        }
        Label("CATEGORIES", Ui.type.overline, color = Ui.colors.inkFaint, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            item {
                SidebarRow("Everything", null, state.query.category == null, false) { viewModel.pickCategory(null) }
            }
            items(state.categories, key = { it.first }) { (name, count) ->
                SidebarRow(name, count, state.query.category == name, false) { viewModel.pickCategory(name) }
            }
        }
        Hairline()
        DeviceLine(state.device, Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp))
        SyncLine(state, onRefresh = viewModel::refresh, modifier = Modifier.padding(start = 20.dp, end = 8.dp, bottom = 8.dp))
    }
}

@Composable
private fun SidebarRow(label: String, count: Int?, selected: Boolean, emphasised: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Ui.colors.sunken else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label, if (selected) Ui.type.bodyStrong else Ui.type.body, Modifier.weight(1f), color = if (selected) Ui.colors.ink else Ui.colors.inkSoft, maxLines = 1)
        if (count != null) {
            if (emphasised) {
                Tag(grouped(count), Ui.colors.accent, Ui.colors.accentTint)
            } else {
                Label(grouped(count), Ui.type.monoSmall, color = Ui.colors.inkFaint)
            }
        }
    }
}

@Composable
fun DeviceLine(device: DeviceState, modifier: Modifier = Modifier) {
    val (text, live) = when (device) {
        DeviceState.Unknown -> "Looking for a device" to false
        DeviceState.AdbMissing -> "adb not found. Install Android platform-tools to install apps." to false
        DeviceState.NoDevice -> "No phone connected" to false
        is DeviceState.Unauthorized -> "Allow USB debugging on the phone" to false
        is DeviceState.Ready -> "${device.name} · Android ${androidRelease(device.profile.sdk)}" to true
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Glyph(Glyphs.Phone, if (live) Ui.colors.positive else Ui.colors.inkFaint, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Label(text, Ui.type.small, color = if (live) Ui.colors.ink else Ui.colors.inkSoft, maxLines = 2)
    }
}

@Composable
fun SyncLine(state: HomeState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val failed = state.sync as? SyncState.Failed
    val text = when (val sync = state.sync) {
        SyncState.Checking -> "Checking the repository"
        is SyncState.Downloading -> "Downloading catalogue ${(sync.bytes * 100 / sync.total.coerceAtLeast(1))}%"
        SyncState.Reading -> "Reading catalogue"
        is SyncState.Failed -> sync.message
        SyncState.Idle -> state.lastChecked?.let { "Checked ${relativeTime(it, nowMillis())}" } ?: "Not checked yet"
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Label(text, Ui.type.small, Modifier.weight(1f), color = if (failed != null) Ui.colors.caution else Ui.colors.inkFaint, maxLines = 3)
        IconButton(Glyphs.Refresh, onRefresh, tint = Ui.colors.inkSoft)
    }
}

@Composable
fun ListHeader(state: HomeState, viewModel: DepotViewModel, compact: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchField(state.query.text, viewModel::search, "Search ${grouped(state.listing.total)} apps", Modifier.weight(1f))
        Spacer(Modifier.width(20.dp))
        SortPicker(state.query.sort, viewModel::sortBy)
    }
}

@Composable
private fun SortPicker(current: SortOrder, onPick: (SortOrder) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SortOrder.entries.forEach { order ->
            val selected = order == current
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(6.dp)).clickable { onPick(order) }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Label(order.label, if (selected) Ui.type.bodyStrong else Ui.type.body, color = if (selected) Ui.colors.ink else Ui.colors.inkFaint, maxLines = 1)
            }
        }
    }
}

@Composable
fun CompactHeader(state: HomeState, viewModel: DepotViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.Crate, Ui.colors.accent, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Label(Brand.NAME, Ui.type.title, Modifier.weight(1f))
            IconButton(Glyphs.Refresh, viewModel::refresh, touch = 48.dp)
        }
        SearchField(state.query.text, viewModel::search, "Search ${grouped(state.listing.total)} apps", Modifier.fillMaxWidth().padding(horizontal = 16.dp), height = 48.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Shelf.entries.forEach { shelf ->
                val selected = state.shelf == shelf
                val count = if (shelf == Shelf.Updates && state.listing.updateCount > 0) " ${state.listing.updateCount}" else ""
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(6.dp)).clickable { viewModel.showShelf(shelf) }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Label(shelf.label + count, if (selected) Ui.type.bodyStrong else Ui.type.body, color = if (selected) Ui.colors.ink else Ui.colors.inkFaint)
                }
            }
            Box(Modifier.padding(horizontal = 6.dp).size(width = 1.dp, height = 18.dp).background(Ui.colors.line))
            SortOrder.entries.forEach { order ->
                val selected = state.query.sort == order
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(6.dp)).clickable { viewModel.sortBy(order) }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Label(order.label, if (selected) Ui.type.bodyStrong else Ui.type.body, color = if (selected) Ui.colors.ink else Ui.colors.inkFaint)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip("Everything", state.query.category == null) { viewModel.pickCategory(null) }
            state.categories.forEach { (name, _) ->
                CategoryChip(name, state.query.category == name) { viewModel.pickCategory(name) }
            }
        }
        if (state.sync != SyncState.Idle) {
            SyncLine(state, viewModel::refresh, Modifier.padding(start = 16.dp, end = 4.dp))
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Ui.colors.ink else Ui.colors.sunken)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(label, Ui.type.small, color = if (selected) Ui.colors.surface else Ui.colors.inkSoft, maxLines = 1)
    }
}

@Composable
fun AppList(state: HomeState, viewModel: DepotViewModel, compact: Boolean) {
    val apps = state.listing.apps
    if (apps.isEmpty()) {
        EmptyList(state)
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.query, state.shelf) { listState.scrollToItem(0) }
    val now = remember(apps) { nowMillis() }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        items(apps, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                status = remember(app, state.device) { app.statusOn(state.device) },
                install = state.installs[app.packageName],
                selected = state.selected?.packageName == app.packageName,
                now = now,
                compact = compact,
                onClick = { viewModel.open(app) },
            )
        }
    }
}

@Composable
private fun EmptyList(state: HomeState) {
    val message = when {
        !state.hasCatalog && state.sync is SyncState.Failed -> "The catalogue could not be loaded.\n${state.sync.message}"
        !state.hasCatalog -> "Fetching the catalogue for the first time"
        state.shelf == Shelf.Updates && state.query.text.isBlank() -> "Everything on the device is up to date"
        state.shelf != Shelf.All && state.device !is DeviceState.Ready -> "Connect a phone to see what is installed on it"
        else -> "Nothing matches"
    }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Label(message, Ui.type.body, color = Ui.colors.inkSoft)
    }
}

@Composable
private fun AppRow(app: CatalogApp, status: AppStatus, install: InstallState?, selected: Boolean, now: Long, compact: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Ui.colors.accentTint else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 20.dp)
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.iconUrl, app.name, 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Label(app.name, Ui.type.heading, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Label(app.summary.ifBlank { app.packageName }, Ui.type.small, color = Ui.colors.inkSoft, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            when {
                install?.busy == true -> Tag("INSTALLING", Ui.colors.accent, Ui.colors.accentTint)
                status is AppStatus.Update -> Tag("UPDATE", Ui.colors.accent, Ui.colors.accentTint)
                status is AppStatus.UpToDate -> Tag("INSTALLED", Ui.colors.positive, Ui.colors.sunken)
                status is AppStatus.NotCompatible -> Tag("NOT COMPATIBLE", Ui.colors.inkFaint, Ui.colors.sunken)
                else -> Label(app.versions.first().versionName, Ui.type.monoSmall, color = Ui.colors.inkSoft, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Label(relativeTime(app.lastUpdated, now), Ui.type.monoSmall, color = Ui.colors.inkFaint, maxLines = 1)
        }
    }
}
