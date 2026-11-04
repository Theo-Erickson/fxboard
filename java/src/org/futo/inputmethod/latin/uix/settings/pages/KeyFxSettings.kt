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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.theo.keyfx.Field
import com.theo.keyfx.Fx
import com.theo.keyfx.FxSettings
import com.theo.keyfx.KeyFx
import com.theo.keyfx.Section
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsTextEdit
import org.futo.inputmethod.latin.uix.settings.BottomSpacer
import org.futo.inputmethod.latin.uix.settings.DropDownPicker
import org.futo.inputmethod.latin.uix.settings.RotatingChevronIcon
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.theme.Typography
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

object KeyFxPrefs {
    /** SharedPreferences file keyfx reads, keys "<sectionId>.<fieldKey>". */
    const val FILE = "keyfx"

    /**
     * What FXboard implements beyond drawing on top. CAP_SUGGESTION_STYLE, CAP_POPUP_LAYOUT and
     * CAP_HIDE_ANIMATION join as FXboard implements them, so this page only shows working settings.
     */
    val CAPS = KeyFx.CAP_KEY_TRANSFORM or KeyFx.CAP_EXACT_WORD

    fun open(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

const val KeyFxSettingsNavPath = "keyfx"

@Composable
fun KeyFxSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { KeyFxPrefs.open(context) }
    val schema = remember { KeyFx.schema() }
    val settings = remember { FxSettings(prefs, schema, KeyFxPrefs.CAPS) }
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
    // Which sections show their settings; outside the version key so it survives each write
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // The section being previewed on its own, null when no preview is open
    var previewing by remember { mutableStateOf<Section?>(null) }
    val previewText = remember { mutableStateOf("") }
    // A backup left by an app killed mid-preview is restored on entering; leaving restores too
    LaunchedEffect(Unit) { restorePreview(prefs, schema, edit) }
    DisposableEffect(Unit) {
        onDispose { restorePreview(prefs, schema, edit) }
    }
    // Switching apps keeps the screen composed, so a preview must also end when it goes to the background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && previewing != null) {
                previewing = null
                previewText.value = ""
                restorePreview(prefs, schema, edit)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

        Box(Modifier.padding(16.dp, 8.dp)) {
            SettingsTextEdit(testText, multiline = true, placeholder = stringResource(R.string.keyfx_test_field_hint))
        }

        var group: String? = null
        for (section in schema) {
            if (!section.supportedBy(KeyFxPrefs.CAPS)) continue
            if (section.group != group) {
                group = section.group
                Spacer(Modifier.height(GroupGap))
                ScreenTitle(section.group)
            } else {
                Spacer(Modifier.height(SectionGap))
            }
            // Keyed on version: settings isn't Compose state, so a write must rebuild the section
            key(version) {
                SectionSettings(
                    section, settings, edit,
                    expanded = expanded[section.id] == true,
                    toggleExpanded = { expanded[section.id] = expanded[section.id] != true },
                    preview = {
                        startPreview(prefs, schema, section, edit)
                        previewing = section
                    },
                )
            }
        }

        Spacer(Modifier.height(GroupGap))
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

    previewing?.let { section ->
        val done = {
            previewing = null
            previewText.value = ""
            restorePreview(prefs, schema, edit)
        }
        AlertDialog(
            onDismissRequest = done,
            title = { Text(stringResource(R.string.keyfx_preview_title, section.title)) },
            text = {
                Column {
                    Text(stringResource(R.string.keyfx_preview_message))
                    Spacer(Modifier.height(12.dp))
                    // An EditText, so autofocus (not a FocusRequester) focuses it and brings up the keyboard
                    SettingsTextEdit(previewText, multiline = true, autofocus = true,
                        placeholder = stringResource(R.string.keyfx_test_field_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = done) { Text(stringResource(R.string.keyfx_preview_done)) }
            },
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

// Space above each group title, and between the features in a group
private val GroupGap = 20.dp
private val SectionGap = 8.dp
// Lines settings up under their feature's title, past SettingItem's icon column
private val FieldIndent = 80.dp

/**
 * One schema section: its switch (or plain header). While it is on, tapping the row (or the
 * chevron) expands its settings below it; the switch itself only turns the feature on and off.
 */
@Composable
private fun SectionSettings(
    section: Section,
    settings: FxSettings,
    edit: (SharedPreferences.Editor.() -> Unit) -> Unit,
    expanded: Boolean,
    toggleExpanded: () -> Unit,
    preview: () -> Unit,
) {
    val fields = section.fields.filter { it.supportedBy(KeyFxPrefs.CAPS) && it.visible(settings, section.id) }
    val on = !section.toggleable || settings.on(section.id)
    val canExpand = on && fields.isNotEmpty()
    val chevron: (@Composable () -> Unit)? = if (canExpand) { { RotatingChevronIcon(expanded) } } else null

    if (section.toggleable) {
        // The switch row, with a Preview button that tries this effect on its own
        Row(verticalAlignment = CenterVertically) {
            Box(Modifier.weight(1.0f)) {
                SettingToggleRaw(
                    title = section.title,
                    enabled = on,
                    setValue = { edit { putBoolean(FxSettings.key(section.id, "on"), it) } },
                    subtitle = section.description,
                    icon = chevron,
                    onSubmenuNavigate = if (canExpand) toggleExpanded else null,
                )
            }
            TextButton(onClick = preview, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(R.string.keyfx_preview))
            }
        }
    } else {
        SettingItem(
            title = section.title,
            subtitle = section.description,
            onClick = if (canExpand) toggleExpanded else null,
            icon = chevron,
        ) {}
    }
    if (!canExpand || !expanded) return

    for (field in fields) {
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

    Column(Modifier.padding(start = FieldIndent, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
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
    // Values that need something FXboard doesn't implement yet aren't offered
    val options = field.values.indices.filter { field.valueMissing(it, KeyFxPrefs.CAPS) == 0 }
    Column(Modifier.padding(start = FieldIndent, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
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

// Prefs key holding the stored on values of toggleable sections while a preview runs, as JSON
private const val PreviewBackup = "preview.backup"

/** Backs up the toggleable sections' stored on values, then turns on only the previewed one. */
private fun startPreview(
    prefs: SharedPreferences,
    schema: List<Section>,
    previewed: Section,
    edit: (SharedPreferences.Editor.() -> Unit) -> Unit,
) {
    // A backup already there is the real one; don't overwrite it with preview values
    val keep = prefs.contains(PreviewBackup)
    // Only values actually stored, so sections still on their defaults go back to defaults
    val backup = JSONObject()
    val stored = prefs.all
    for (section in schema) {
        if (!section.toggleable) continue
        val v = stored[FxSettings.key(section.id, "on")]
        if (v is Boolean) backup.put(section.id, v)
    }
    edit {
        if (!keep) putString(PreviewBackup, backup.toString())
        for (section in schema) {
            if (section.toggleable) putBoolean(FxSettings.key(section.id, "on"), section === previewed)
        }
    }
}

/**
 * Puts back the on values saved by startPreview, removes the ones that weren't stored so they
 * read as defaults again, and deletes the backup; nothing to do without one.
 */
private fun restorePreview(
    prefs: SharedPreferences,
    schema: List<Section>,
    edit: (SharedPreferences.Editor.() -> Unit) -> Unit,
) {
    val saved = prefs.getString(PreviewBackup, null) ?: return
    // A corrupt backup can't be restored, so it is just dropped
    val backup = try { JSONObject(saved) } catch (e: Exception) { null }
    edit {
        if (backup != null) {
            for (section in schema) {
                if (!section.toggleable) continue
                val key = FxSettings.key(section.id, "on")
                if (backup.has(section.id)) putBoolean(key, backup.optBoolean(section.id)) else remove(key)
            }
        }
        remove(PreviewBackup)
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
                    // Values older lab exports still use are converted to what replaced them
                    val v = Fx.migrate(key, o.optString(field.key, ""))
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
