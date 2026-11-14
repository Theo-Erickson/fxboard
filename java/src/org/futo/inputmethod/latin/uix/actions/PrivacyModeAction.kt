package org.futo.inputmethod.latin.uix.actions

import android.widget.Toast
import androidx.core.content.edit
import com.theo.keyfx.KeyFx
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.settings.pages.KeyFxPrefs

/**
 * Fork: turns keyfx's privacy mode on or off by hand, so effects stay off in every field.
 * It flips the same "Always on" switch as the Privacy card in the effects settings; the
 * keyboard's KeyFxHost picks the change up from the prefs listener.
 */
val PrivacyModeAction = Action(
    icon = R.drawable.eye_off,
    name = R.string.action_privacy_mode_title,
    simplePressImpl = { manager, _ ->
        val context = manager.getContext()
        val prefs = KeyFxPrefs.open(context)
        val on = !prefs.getBoolean(KeyFx.PRIVACY_ALWAYS_KEY, false)
        prefs.edit { putBoolean(KeyFx.PRIVACY_ALWAYS_KEY, on) }
        val message = context.getString(if (on) R.string.action_privacy_mode_on else R.string.action_privacy_mode_off)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    },
    windowImpl = null,
)
