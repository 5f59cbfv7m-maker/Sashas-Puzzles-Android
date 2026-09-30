package com.kirillrychkov.sashaspuzzles.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.app.AppModel
import com.kirillrychkov.sashaspuzzles.game.switchColors
import com.kirillrychkov.sashaspuzzles.model.Difficulty
import com.kirillrychkov.sashaspuzzles.model.PuzzleAspect
import com.kirillrychkov.sashaspuzzles.model.SnapAssist
import com.kirillrychkov.sashaspuzzles.ui.Kicker
import com.kirillrychkov.sashaspuzzles.ui.PillButton
import com.kirillrychkov.sashaspuzzles.ui.PillSegments
import com.kirillrychkov.sashaspuzzles.ui.Theme
import com.kirillrychkov.sashaspuzzles.ui.pressable
import com.kirillrychkov.sashaspuzzles.ui.substituted

@Composable
fun SettingsScreen(model: AppModel, onDone: () -> Unit) {
    val colors = Theme.colors
    val settings = model.settings
    var confirmReset by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(colors.surface).clickable(remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 620.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings), style = Theme.display(28), color = colors.text, modifier = Modifier.weight(1f))
                PillButton(stringResource(R.string.done), size = 15, onClick = onDone)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Group(stringResource(R.string.appearance)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.theme), style = Theme.body(16), color = colors.text)
                        PillSegments(AppSettings.Appearance.entries, settings.appearance, { stringResource(it.title) }, expand = true) { settings.appearance = it }
                    }
                    Divider()
                    Toggle(stringResource(R.string.picture_guide_on_the_table), settings.showGhostImage) { settings.showGhostImage = it }
                    Divider()
                    Toggle(stringResource(R.string.outline_pieces), settings.showPieceOutlines) { settings.showPieceOutlines = it }
                }
                Group(stringResource(R.string.feedback)) {
                    Toggle(stringResource(R.string.sounds), settings.soundEnabled) { settings.soundEnabled = it }
                    Divider()
                    Toggle(stringResource(R.string.background_music), settings.musicEnabled) { settings.musicEnabled = it }
                    if (settings.musicEnabled) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.music_at_the_table), style = Theme.body(16), color = colors.text)
                            PillSegments(AppSettings.BoardMusic.entries, settings.boardMusic, { stringResource(it.title) }, expand = true) { settings.boardMusic = it }
                        }
                    }
                    Divider()
                    Toggle(stringResource(R.string.haptic_feedback), settings.hapticsEnabled) { settings.hapticsEnabled = it }
                }
                Group(stringResource(R.string.gameplay)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row {
                            Text(stringResource(R.string.snap_assist), style = Theme.body(16), color = colors.text, modifier = Modifier.weight(1f))
                            Text(stringResource(settings.snapAssist.title), style = Theme.body(14, FontWeight.Bold), color = colors.accentDeep)
                        }
                        PillSegments(SnapAssist.entries, settings.snapAssist, { stringResource(it.title) }, expand = true) { settings.snapAssist = it }
                    }
                    Divider()
                    Line(stringResource(R.string.default_difficulty)) {
                        Choice(Difficulty.entries, settings.defaultDifficulty, { "${stringResource(it.title)} · ${it.targetPieces}" }) { settings.defaultDifficulty = it }
                    }
                    Divider()
                    Line(stringResource(R.string.default_framing)) {
                        Choice(PuzzleAspect.entries, settings.defaultAspect, { a -> a.titleRes?.let { stringResource(it) } ?: a.label }) { settings.defaultAspect = it }
                    }
                }
                Group(stringResource(R.string.saved_games)) {
                    Line(stringResource(R.string.saved_games)) { Count(model.savedGames.size) }
                    Divider()
                    Line(stringResource(R.string.puzzles_solved)) { Count(model.stats.puzzlesSolved) }
                    Divider()
                    Text(stringResource(R.string.reset_saved_games_and_statistics), style = Theme.body(16, FontWeight.Bold), color = colors.accentDeep,
                        modifier = Modifier.fillMaxWidth().pressable { confirmReset = true }.padding(horizontal = 14.dp, vertical = 13.dp))
                }
                Text(
                    substituted(R.string.sashas_puzzles_x_built_in_pictures_x_of_your_pho, model.library.builtIn.size, 0,
                        plural = mapOf(1 to R.plurals.sashas_puzzles_x_built_in_pictures_x_of_your_pho_arg1,
                            2 to R.plurals.sashas_puzzles_x_built_in_pictures_x_of_your_pho_arg2)),
                    style = Theme.body(13), color = colors.faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = colors.card,
            title = { Text(stringResource(R.string.delete_all_saved_games_and_statistics), style = Theme.body(18, FontWeight.Bold), color = colors.text) },
            confirmButton = {
                TextButton({ confirmReset = false; model.deleteAllSaves(); model.stats.reset() }) {
                    Text(stringResource(R.string.delete), style = Theme.body(16, FontWeight.Bold), color = colors.accentDeep)
                }
            },
            dismissButton = {
                TextButton({ confirmReset = false }) { Text(stringResource(R.string.cancel), style = Theme.body(16), color = colors.text) }
            },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kicker(title, Modifier.padding(horizontal = 12.dp))
        Column(Modifier.fillMaxWidth().background(Theme.colors.card, RoundedCornerShape(22.dp)).padding(6.dp), content = content)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Theme.colors.hairline))
}

@Composable
private fun Line(title: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = Theme.body(16), color = Theme.colors.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Toggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().pressable { onChange(!value) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Theme.body(16), color = Theme.colors.text, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = switchColors())
    }
}

@Composable
private fun Count(value: Int) {
    Text("$value", style = Theme.body(14, FontWeight.Bold), color = Theme.colors.muted,
        modifier = Modifier.background(Theme.colors.surface, CircleShape).padding(horizontal = 11.dp, vertical = 3.dp))
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, title: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val colors = Theme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Text(title(selected), style = Theme.body(15, FontWeight.Bold), color = colors.accentDeep,
            modifier = Modifier.pressable { open = true }.padding(vertical = 4.dp))
        DropdownMenu(open, { open = false }, containerColor = colors.card) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(title(option), style = Theme.body(15, if (option == selected) FontWeight.Bold else FontWeight.Normal), color = colors.text) },
                    onClick = { open = false; onSelect(option) },
                )
            }
        }
    }
}
