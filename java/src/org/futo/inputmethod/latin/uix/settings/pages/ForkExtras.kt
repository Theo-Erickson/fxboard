// Fork addition (Theo's FUTO Keyboard fork): settings page for everything this fork adds.
// Kept in its own file so merging upstream FUTO changes rarely conflicts with it.

package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingToggleSharedPrefs

val ForkExtrasMenu = UserSettingsMenu(
    title = R.string.fork_settings_title,
    navPath = "forkExtras", registerNavPath = true,
    settings = listOf(
        // Modified-version notice, required by the FUTO Source First License
        userSettingDecorationOnly {
            Tip(stringResource(R.string.fork_modified_notice))
        },

        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.fork_settings_cursor_section))
        },

        // Defaults come from SettingsValues so the toggle and the keyboard always agree
        userSettingToggleSharedPrefs(
            title = R.string.fork_spacebar_vertical_title,
            subtitle = R.string.fork_spacebar_vertical_subtitle,
            key = Settings.PREF_SPACEBAR_VERTICAL_CURSOR,
            default = { remember { Settings.getInstance().current.mSpacebarVerticalCursor } },
        ),
        userSettingToggleSharedPrefs(
            title = R.string.fork_hide_nav_bar_title,
            subtitle = R.string.fork_hide_nav_bar_subtitle,
            key = Settings.PREF_HIDE_IME_NAV_BAR,
            default = { remember { Settings.getInstance().current.mHideImeNavBar } },
        ),
    )
)
