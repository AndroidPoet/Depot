package dev.androidpoet.depot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.androidpoet.depot.engine.Depot

@Composable
fun DepotApp(
    depot: Depot,
    dark: Boolean? = null,
    initialPackage: String? = null,
    onBack: @Composable (enabled: Boolean, action: () -> Unit) -> Unit = { _, _ -> },
) {
    DepotTheme(dark) {
        val viewModel = viewModel { DepotViewModel(depot, initialPackage) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        onBack(state.selected != null, viewModel::closeDetail)
        BoxWithConstraints(Modifier.fillMaxSize().background(Ui.colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(state, viewModel, Modifier.width(236.dp).fillMaxHeight())
                    VerticalHairline(Modifier.fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight().background(Ui.colors.surface)) {
                        ListHeader(state, viewModel, compact = false)
                        Hairline()
                        AppList(state, viewModel, compact = false)
                    }
                    state.selected?.let { app ->
                        VerticalHairline(Modifier.fillMaxHeight())
                        DetailPane(app, state, viewModel, compact = false, modifier = Modifier.width(440.dp).fillMaxHeight())
                    }
                }
            } else {
                val selected = state.selected
                if (selected != null) {
                    DetailPane(selected, state, viewModel, compact = true, modifier = Modifier.fillMaxSize())
                } else {
                    Column(Modifier.fillMaxSize().background(Ui.colors.surface)) {
                        CompactHeader(state, viewModel)
                        Hairline()
                        Box(Modifier.weight(1f)) { AppList(state, viewModel, compact = true) }
                    }
                }
            }
        }
    }
}
