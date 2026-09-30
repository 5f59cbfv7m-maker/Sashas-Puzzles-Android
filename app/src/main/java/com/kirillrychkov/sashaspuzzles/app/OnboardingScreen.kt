package com.kirillrychkov.sashaspuzzles.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kirillrychkov.sashaspuzzles.R
import com.kirillrychkov.sashaspuzzles.library.LibraryThumbnail
import com.kirillrychkov.sashaspuzzles.ui.Blob
import com.kirillrychkov.sashaspuzzles.ui.PillButton
import com.kirillrychkov.sashaspuzzles.ui.PuzzleMark
import com.kirillrychkov.sashaspuzzles.ui.Tag
import com.kirillrychkov.sashaspuzzles.ui.Theme

/** Three steps on first launch; each shows the mechanic rather than describing it. */
@Composable
fun OnboardingScreen(model: AppModel, onFinish: () -> Unit) {
    val colors = Theme.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = 2
    val count = model.library.builtIn.size
    val titles = listOf(R.string.real_locks, R.string.just_the_help_you_need, R.string.your_photos_are_puzzles_too)

    Box(Modifier.fillMaxSize().background(colors.bg).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Blob(Modifier.align(Alignment.Center).offset(160.dp, (-260).dp), size = 240.dp)
        Blob(Modifier.align(Alignment.Center).offset((-170).dp, 60.dp), color = colors.blob2, size = 230.dp)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(step, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "illustration") {
                    Illustration(model, it)
                }
            }
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(start = 34.dp, end = 34.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Tag(stringResource(R.string.step_x_of_x, step + 1, last + 1))
                Text(stringResource(titles[step]), style = Theme.display(30), color = colors.text, modifier = Modifier.padding(top = 4.dp))
                val text = when (step) {
                    0 -> stringResource(R.string.every_cut_is_made_once_and_shared_by_its_two_nei)
                    1 -> stringResource(R.string.a_piece_pulls_itself_into_place_the_hint_highlig)
                    else -> pluralStringResource(R.plurals.x_pictures_in_the_library_plus_any_photo_from_yo, count, count)
                }
                Text(text, style = Theme.body(16), color = colors.muted)
                if (step == last) {
                    BasicTextField(
                        model.settings.playerName, { model.settings.playerName = it.take(24) },
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(CircleShape).background(colors.surface).padding(horizontal = 18.dp, vertical = 13.dp),
                        textStyle = Theme.body(17).copy(color = colors.text),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onFinish() }),
                        decorationBox = { field ->
                            if (model.settings.playerName.isEmpty()) {
                                Text(stringResource(R.string.what_should_we_call_you), style = Theme.body(17), color = colors.faint)
                            }
                            field()
                        },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (index in 0..last) {
                        Box(Modifier.height(8.dp).width(if (index == step) 28.dp else 8.dp).clip(CircleShape).background(if (index == step) colors.accent else colors.track))
                    }
                    Spacer(Modifier.weight(1f))
                    PillButton(stringResource(if (step == last) R.string.to_the_table else R.string.next), size = 16) {
                        if (step < last) step++ else onFinish()
                    }
                }
            }
        }
    }
}

@Composable
private fun Illustration(model: AppModel, step: Int) {
    val colors = Theme.colors
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "t")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(250.dp).background(colors.surface, CircleShape))
        when (step) {
            0 -> PuzzleMark(Modifier.offset(y = (-5 * pulse).dp), size = 170.dp)
            1 -> {
                Box(Modifier.size(210.dp, 156.dp).background(colors.card, RoundedCornerShape(20.dp)))
                Box(Modifier.size(90.dp).scale(0.94f + 0.1f * pulse)
                    .background(colors.accent.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                    .border(4.dp, colors.accent.copy(alpha = 0.4f + 0.6f * pulse), RoundedCornerShape(16.dp)))
                PuzzleMark(Modifier.offset((86 + 6 * pulse).dp, (80 - 10 * pulse).dp).rotate(3 * pulse), size = 120.dp)
            }
            else -> {
                LibraryThumbnail(model, model.library.dailyItem(),
                    Modifier.offset(y = (-5 * pulse).dp).size(150.dp).shadow(16.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)), washed = true)
                Box(Modifier.offset(80.dp, 68.dp).size(62.dp).shadow(9.dp, CircleShape).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = colors.onAccent, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
