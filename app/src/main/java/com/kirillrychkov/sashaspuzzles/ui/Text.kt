package com.kirillrychkov.sashaspuzzles.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Clock and count formatting shared by every screen. */
object TimeFormatting {
    fun clock(millis: Long): String {
        val total = (maxOf(0L, millis) / 1000).toInt()
        return String.format(Locale.ROOT, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }

    /** `m:ss`, for differences that are never hours long. */
    fun short(millis: Long): String {
        val total = (maxOf(0L, millis) / 1000).toInt()
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
    }
}

/**
 * A string whose numbers some languages spell with their own plural forms.
 *
 * iOS writes those as substitutions inside one string; Android has no plurals
 * inside a string, so the import script splits each such number into its own
 * `<plurals>` named `<string>_argN`. `args` are in the English order; a number
 * listed in `plural` is spelled through its plurals resource first.
 */
@Composable
fun substituted(string: Int, vararg args: Any, plural: Map<Int, Int> = emptyMap()): String {
    val resources = LocalContext.current.resources
    val resolved = args.mapIndexed { index, value ->
        val pluralRes = plural[index + 1]
        if (pluralRes != null && value is Int) resources.getQuantityString(pluralRes, value, value) else value.toString()
    }
    return resources.getString(string, *resolved.toTypedArray())
}
