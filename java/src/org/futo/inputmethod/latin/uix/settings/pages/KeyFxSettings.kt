// FXboard addition: settings for keyfx, the effects library, generated from its schema so every
// effect setting shows up here without screen code of its own.
// Kept in its own file so merging upstream FUTO changes rarely conflicts with it.

package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.theo.keyfx.Field
import com.theo.keyfx.FxSettings
import com.theo.keyfx.KeyFx
import com.theo.keyfx.Section
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.SettingsTextEdit
import org.futo.inputmethod.latin.uix.settings.BottomSpacer
import org.futo.inputmethod.latin.uix.settings.DropDownPicker
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.SettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.theme.Typography
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

object KeyFxPrefs {
    /** SharedPreferences file keyfx reads, keys "<sectionId>.<fieldKey>". */
    const val FILE = "keyfx"

    /** FXboard is the fork, so it can do everything keyfx asks of a host. */
    val CAPS = KeyFx.CAP_KEY_TRANSFORM or KeyFx.CAP_SUGGESTION_STYLE or KeyFx.CAP_POPUP_LAYOUT or
            KeyFx.CAP_EXACT_WORD or KeyFx.CAP_HIDE_ANIMATION

    fun open(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

const val KeyFxSettingsNavPath = "keyfx"

@Composable
fun KeyFxSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { KeyFxPrefs.open(context) }
    val schema = remember { KeyFx.schema() }
    val settings = remember { FxSettings(prefs, schema) }
    // Bumped after every write, so values read from settings recompose
    var version by remember { mutableIntStateOf(0) }
    val edit: (SharedPreferences.Editor.() -> Unit) -> Unit = { block ->
        prefs.edit(action = block)
        settings.reload()
        version++
    }

    var showImport by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    val testText = remember { mutableStateOf("") }

    ScrollableList {
        Row(verticalAlignment = CenterVertically) {
            Box(Modifier.weight(1.0f)) {
                ScreenTitle(stringResource(R.string.keyfx_settings_title), showBack = true)
            }
            TextButton(onClick = { showImport = true }) { Text(stringResource(R.string.keyfx_import)) }
            TextButton(onClick = { showReset = true }) { Text(stringResource(R.string.keyfx_reset)) }
            Spacer(Modifier.width(8.dp))
        }

        // Modified-version notice, required by the FUTO Source First License
        Tip(stringResource(R.string.fork_modified_notice))
        Tip(stringResource(R.string.keyfx_not_connected))

        Box(Modifier.padding(16.dp, 8.dp)) {
            SettingsTextEdit(testText, multiline = true, placeholder = stringResource(R.string.keyfx_test_field_hint))
        }

        var group: String? = null
        for (section in schema) {
            if (!section.supportedBy(KeyFxPrefs.CAPS)) continue
            if (section.group != group) {
                group = section.group
                ScreenTitle(section.group)
            }
            SectionSettings(section, settings, version, edit)
        }

        ScreenTitle(stringResource(R.string.keyfx_legacy_section))
        Text(
            stringResource(R.string.keyfx_legacy_note),
            style = Typography.SmallMl,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 0.dp)
        )
        SettingToggleSharedPrefs(
            title = stringResource(R.string.fork_effect_ripple_title),
            subtitle = stringResource(R.string.fork_effect_ripple_subtitle),
            key = Settings.PREF_EFFECT_RIPPLE,
            default = false,
        )
        SettingToggleSharedPrefs(
            title = stringResource(R.string.fork_effect_glow_title),
            subtitle = stringResource(R.string.fork_effect_glow_subtitle),
            key = Settings.PREF_EFFECT_GLOW,
            default = false,
        )
        SettingToggleSharedPrefs(
            title = stringResource(R.string.fork_effect_particles_title),
            subtitle = stringResource(R.string.fork_effect_particles_subtitle),
            key = Settings.PREF_EFFECT_PARTICLES,
            default = false,
        )
        SettingToggleSharedPrefs(
            title = stringResource(R.string.fork_effect_trail_title),
            subtitle = stringResource(R.string.fork_effect_trail_subtitle),
            key = Settings.PREF_EFFECT_SPACE_TRAIL,
            default = false,
        )

        Tip(stringResource(R.string.keyfx_animations_note))
        BottomSpacer()
    }

    if (showImport) {
        val pasted = remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text(stringResource(R.string.keyfx_import_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.keyfx_import_message))
                    Spacer(Modifier.height(12.dp))
                    SettingsTextEdit(pasted, multiline = true, autocorrect = false,
                        placeholder = stringResource(R.string.keyfx_import_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImport = false
                    try {
                        var count = 0
                        edit { count = importLab(this, schema, pasted.value) }
                        Toast.makeText(context, context.getString(R.string.keyfx_import_done, count), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.keyfx_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.keyfx_import)) }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.keyfx_reset_title)) },
            text = { Text(stringResource(R.string.keyfx_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReset = false
                    edit { clear() }
                }) { Text(stringResource(R.string.keyfx_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

/** One schema section: its switch (or plain header), then its fields while it is on. */
@Composable
private fun SectionSettings(
    section: Section,
    settings: FxSettings,
    @Suppress("UNUSED_PARAMETER") version: Int, // forces recomposition after a write
    edit: (SharedPreferences.Editor.() -> Unit) -> Unit
) {
    if (section.toggleable) {
        val on = settings.on(section.id)
        SettingToggleRaw(
            title = section.title,
            enabled = on,
            setValue = { edit { putBoolean(FxSettings.key(section.id, "on"), it) } },
            subtitle = section.description,
        )
        if (!on) return
    } else {
        SettingItem(title = section.title, subtitle = section.description) {}
    }

    for (field in section.fields) {
        if (!field.supportedBy(KeyFxPrefs.CAPS) || !field.visible(settings, section.id)) continue
        val key = FxSettings.key(section.id, field.key)
        when (field.type) {
            Field.TOGGLE -> SettingToggleRaw(
                title = field.label,
                enabled = settings.b(section.id, field.key),
                setValue = { edit { putBoolean(key, it) } },
                compact = true,
            )
            Field.RANGE -> RangeField(field, settings.f(section.id, field.key)) { edit { putFloat(key, it) } }
            else -> ChoiceField(field, settings.s(section.id, field.key)) { edit { putString(key, it) } }
        }
    }
}

/** A slider that snaps to the field's step and saves when the drag ends, not on every frame. */
@Composable
private fun RangeField(field: Field, saved: Float, save: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = (dragging ?: saved).coerceIn(field.min, field.max)

    Column(Modifier.padding(16.dp, 4.dp)) {
        Row {
            Text(field.label, style = Typography.Body.Regular, modifier = Modifier.weight(1.0f))
            Text(format(field, value), style = Typography.SmallMl, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = {
                dragging = (field.min + ((it - field.min) / field.step).roundToInt() * field.step)
                    .coerceIn(field.min, field.max)
            },
            onValueChangeFinished = {
                dragging?.let(save)
                dragging = null
            },
            valueRange = field.min..field.max,
        )
    }
}

@Composable
private fun ChoiceField(field: Field, current: String, save: (String) -> Unit) {
    val options = field.values.indices.toList()
    Column(Modifier.padding(16.dp, 4.dp)) {
        Text(field.label, style = Typography.Body.Regular)
        Spacer(Modifier.height(4.dp))
        DropDownPicker(
            options = options,
            // Colors imported from the lab may not be one of the presets; they show as no selection
            selection = options.firstOrNull { field.values[it] == current },
            onSet = { save(field.values[it]) },
            getDisplayName = { field.names[it] },
        )
    }
}

/** Like the lab: whole numbers for whole steps, otherwise one or two decimals. */
private fun format(field: Field, v: Float): String {
    val n = when {
        field.step >= 1f -> v.roundToInt().toString()
        field.step >= 0.1f -> String.format(Locale.US, "%.1f", v)
        else -> String.format(Locale.US, "%.2f", v)
    }
    return if (field.unit.isEmpty()) n else "$n ${field.unit}"
}

/**
 * Applies the lab export's "Full settings:" JSON, whose section ids and keys match the schema.
 * Ranges are clamped, unknown choices skipped. Returns how many settings were applied.
 */
private fun importLab(editor: SharedPreferences.Editor, schema: List<Section>, text: String): Int {
    val marker = text.indexOf("Full settings:")
    val tail = if (marker >= 0) text.substring(marker) else text
    val start = tail.indexOf('{')
    val end = tail.lastIndexOf('}')
    require(start >= 0 && end > start) { "no settings found" }
    val all = JSONObject(tail.substring(start, end + 1))

    var count = 0
    for (section in schema) {
        val o = all.optJSONObject(section.id) ?: continue
        if (section.toggleable && o.has("on")) {
            editor.putBoolean(FxSettings.key(section.id, "on"), o.optBoolean("on"))
            count++
        }
        for (field in section.fields) {
            if (!o.has(field.key)) continue
            val key = FxSettings.key(section.id, field.key)
            when (field.type) {
                Field.TOGGLE -> editor.putBoolean(key, o.optBoolean(field.key))
                Field.RANGE -> {
                    val v = o.optDouble(field.key, Double.NaN)
                    if (v.isNaN()) continue
                    editor.putFloat(key, v.toFloat().coerceIn(field.min, field.max))
                }
                else -> {
                    val v = o.optString(field.key, "")
                    // Colors outside the presets still work, they just don't show as selected
                    if (!v.startsWith("#") && v !in field.values) continue
                    editor.putString(key, v)
                }
            }
            count++
        }
    }
    return count
}
