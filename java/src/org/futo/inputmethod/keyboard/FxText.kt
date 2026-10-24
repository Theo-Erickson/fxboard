// FXboard addition: what a backspace deleted, for keyfx's backspace effects. Ported from FUTO FX's
// Caret, without its cursor-position hook: the letters are laid out along the top of the keyboard.
package org.futo.inputmethod.keyboard

import android.view.inputmethod.InputConnection
import com.theo.keyfx.FxLetter

object FxText {
    /** How much text before the cursor to compare; also the most letters one press can animate. */
    private const val WINDOW = 64

    /** The text before the cursor, or null when the app won't say. */
    fun textBefore(ic: InputConnection?): String? = try {
        ic?.getTextBeforeCursor(WINDOW, 0)?.toString()
    } catch (t: Throwable) {
        null
    }

    /** The least text a full window match must share, so unrelated text doesn't count as a delete. */
    private const val MIN_OVERLAP = 16

    /**
     * How many chars were removed from the end of [before] to leave [after]. Exact when the text
     * is shorter than the window; otherwise found by matching the shifted window, which must share
     * at least [MIN_OVERLAP] chars, or 0.
     */
    fun deletedLength(before: String?, after: String?): Int {
        if (before == null || after == null) return 0
        if (before.length < WINDOW) {
            return if (after.length <= before.length && before.startsWith(after)) before.length - after.length else 0
        }
        for (d in 0..(before.length - MIN_OVERLAP)) {
            if (after.endsWith(before.substring(0, before.length - d))) return d
        }
        return 0
    }

    /**
     * The last [chars] of [before] as letters in text order, in a row centered on [centerX] just
     * above [top]. Line breaks are skipped; a lone deleted space shows as a dot.
     */
    fun deletedLetters(before: String?, chars: Int, centerX: Float, top: Float, density: Float): Array<FxLetter> {
        if (before == null || chars <= 0) return emptyArray()
        val tail = before.substring(maxOf(0, before.length - chars))
        val cps = tail.codePoints().toArray()
        val size = 17 * density
        val w = size * 0.55f
        val y = maxOf(8 * density, top - size * 0.6f)
        val out = ArrayList<FxLetter>()
        for (i in cps.indices) {
            val cp = cps[i]
            if (cp == '\n'.code || (cp == ' '.code && cps.size > 1)) continue
            val ch = if (cp == ' '.code) "·" else String(Character.toChars(cp))
            out.add(FxLetter(ch, centerX + (i - (cps.size - 1) / 2f) * w, y, w, size))
        }
        return out.toTypedArray()
    }
}
