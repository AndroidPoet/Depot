package dev.androidpoet.depot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.androidpoet.depot.catalog.AppVersion
import dev.androidpoet.depot.catalog.CatalogApp
import dev.androidpoet.depot.engine.DeviceState
import dev.androidpoet.depot.engine.InstallState

@Composable
fun DetailPane(app: CatalogApp, state: HomeState, viewModel: DepotViewModel, compact: Boolean, modifier: Modifier = Modifier) {
    val status = remember(app, state.device) { app.statusOn(state.device) }
    val install = state.installs[app.packageName]
    val shown = when (status) {
        is AppStatus.Available -> status.version
        is AppStatus.Update -> status.version
        else -> app.versions.first()
    }
    Column(modifier.background(Ui.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                IconButton(Glyphs.Back, viewModel::closeDetail, touch = 48.dp)
            } else {
                Spacer(Modifier.weight(1f))
                IconButton(Glyphs.Close, viewModel::closeDetail)
            }
        }
        key(app.packageName) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.iconUrl, app.name, 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Label(app.name, Ui.type.title, maxLines = 2)
                        app.authorName?.let { Label(it, Ui.type.small, color = Ui.colors.inkSoft, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Label(app.summary, Ui.type.body, color = Ui.colors.inkSoft)
                Spacer(Modifier.height(20.dp))
                InstallBlock(app, status, install, state.device, viewModel)
                Spacer(Modifier.height(24.dp))
                Facts(app, shown)
                AntiFeatures(shown)
                Section("ABOUT")
                SelectionContainer {
                    Label(remember(app.description) { htmlToPlain(app.description) }.ifBlank { "No description." }, Ui.type.body)
                }
                Links(app)
                Section("VERSIONS")
                val now = remember(app) { nowMillis() }
                app.versions.take(8).forEach { version ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label(version.versionName, Ui.type.mono, Modifier.weight(1f), maxLines = 1)
                        if (version.beta) {
                            Tag("BETA", Ui.colors.inkSoft, Ui.colors.sunken)
                            Spacer(Modifier.width(10.dp))
                        }
                        Label(byteSize(version.size), Ui.type.monoSmall, color = Ui.colors.inkFaint)
                        Spacer(Modifier.width(14.dp))
                        Label(relativeTime(version.added, now), Ui.type.monoSmall, Modifier.width(64.dp), color = Ui.colors.inkFaint, maxLines = 1)
                    }
                    Hairline()
                }
            }
        }
    }
}

@Composable
private fun InstallBlock(app: CatalogApp, status: AppStatus, install: InstallState?, device: DeviceState, viewModel: DepotViewModel) {
    Column(Modifier.fillMaxWidth().height(76.dp), verticalArrangement = Arrangement.Top) {
        when {
            install is InstallState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuietAction("Try again", onClick = {
                        viewModel.cancelInstall(app)
                        targetVersion(status)?.let { viewModel.install(app, it) }
                    })
                }
                Spacer(Modifier.height(8.dp))
                Label(install.reason, Ui.type.small, color = Ui.colors.caution, maxLines = 2)
            }
            install != null && install.busy -> {
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label(
                        text = when (install) {
                            is InstallState.Downloading -> "Downloading ${(install.fraction * 100).toInt()}%"
                            InstallState.Verifying -> "Checking the download"
                            InstallState.Installing -> "Installing"
                            InstallState.AwaitingConfirmation -> "Confirm on the device"
                            else -> ""
                        },
                        style = Ui.type.bodyStrong,
                        modifier = Modifier.weight(1f),
                    )
                    QuietAction("Cancel", onClick = { viewModel.cancelInstall(app) })
                }
                Spacer(Modifier.height(8.dp))
                ProgressLine((install as? InstallState.Downloading)?.fraction)
            }
            device !is DeviceState.Ready -> {
                Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Glyphs.Phone, Ui.colors.inkFaint, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Label(
                        text = if (device == DeviceState.AdbMissing) "adb was not found on this computer" else "Connect a phone with USB debugging to install",
                        style = Ui.type.body,
                        color = Ui.colors.inkSoft,
                    )
                }
            }
            status is AppStatus.Available -> FilledAction("Install ${status.version.versionName}", { viewModel.install(app, status.version) }, glyph = Glyphs.Download)
            status is AppStatus.Update -> FilledAction("Update to ${status.version.versionName}", { viewModel.install(app, status.version) }, glyph = Glyphs.Download)
            status is AppStatus.UpToDate -> Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.Check, Ui.colors.positive, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Label("Installed and up to date", Ui.type.bodyStrong, color = Ui.colors.positive)
            }
            else -> Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("No version of this app runs on the device", Ui.type.body, color = Ui.colors.inkSoft)
            }
        }
    }
}

private fun targetVersion(status: AppStatus): AppVersion? = when (status) {
    is AppStatus.Available -> status.version
    is AppStatus.Update -> status.version
    else -> null
}

@Composable
private fun Facts(app: CatalogApp, version: AppVersion) {
    val facts = listOf(
        "Version" to version.versionName,
        "Size" to byteSize(version.size),
        "Requires" to "Android ${androidRelease(version.minSdk)}+",
        "Licence" to app.license.ifBlank { "Unknown" },
        "Package" to app.packageName,
    )
    Hairline()
    facts.forEach { (label, value) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Label(label, Ui.type.small, Modifier.width(88.dp), color = Ui.colors.inkFaint)
            SelectionContainer(Modifier.weight(1f)) { Label(value, Ui.type.mono, maxLines = 2) }
        }
        Hairline()
    }
}

@Composable
private fun AntiFeatures(version: AppVersion) {
    if (version.antiFeatures.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ui.colors.cautionTint).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        version.antiFeatures.forEach { key ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.Caution, Ui.colors.caution, size = 16.dp)
                Spacer(Modifier.width(10.dp))
                Label(antiFeatureMeaning(key), Ui.type.small, color = Ui.colors.caution)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Label(title, Ui.type.overline, color = Ui.colors.inkFaint, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
}

@Composable
private fun Links(app: CatalogApp) {
    val links = listOfNotNull(app.sourceCode?.let { "Source code" to it }, app.webSite?.let { "Website" to it })
        .filter { it.second.startsWith("https://") || it.second.startsWith("http://") }
    if (links.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { (label, url) ->
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(8.dp)).clickable { runCatching { uriHandler.openUri(url) } }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(label, Ui.type.bodyStrong, color = Ui.colors.accent)
                Spacer(Modifier.width(4.dp))
                Glyph(Glyphs.Outward, Ui.colors.accent, size = 16.dp)
            }
        }
    }
}
