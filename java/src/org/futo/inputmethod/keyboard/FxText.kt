// FXboard addition: what a backspace deleted, and where it was on screen, for keyfx's backspace
// effects. Ported from FUTO Tweaks' Caret. The position comes from the app's CursorAnchorInfo;
// apps that don't report it get the letters along the top of the keyboard instead.
package org.futo.inputmethod.keyboard

import android.graphics.Matrix
import android.graphics.RectF
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.InputConnection
import com.theo.keyfx.FxLetter

object FxText {
    /** The app's latest cursor report, kept by LatinIME.onUpdateCursorAnchorInfo. */
    @Volatile
    var anchor: CursorAnchorInfo? = null

    /** Asks the app to keep reporting where the cursor is. Apps that don't support it just don't. */
    fun watchCursor(ic: InputConnection?) {
        anchor = null
        try {
            ic?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR or InputConnection.CURSOR_UPDATE_IMMEDIATE)
        } catch (t: Throwable) {
            // Optional: letters then start from the top of the keyboard
        }
    }

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

    /**
     * The last [chars] of [before] as letters in text order, where they sat in the app's text, in
     * [root]'s coordinates. [info] must be the cursor report from before the delete. Apps usually
     * report exact bounds only for the word being composed; other letters are placed leftward from
     * the cursor by an estimated width. Text outside the keyboard's window is pinned to its top edge.
     * Without a usable report this falls back to [deletedLetters] at [fallbackX], [fallbackTop].
     */
    fun lettersAt(info: CursorAnchorInfo?, before: String?, chars: Int, root: View?, density: Float,
                  fallbackX: Float, fallbackTop: Float): Array<FxLetter> {
        if (before == null || chars <= 0) return emptyArray()
        val mx = info?.insertionMarkerHorizontal ?: Float.NaN
        if (info == null || root == null || mx.isNaN()) return deletedLetters(before, chars, fallbackX, fallbackTop, density)
        val m = info.matrix
        val selStart = info.selectionStart
        val cursor = toRoot(floatArrayOf(mx, info.insertionMarkerTop, mx, info.insertionMarkerBottom), m, root)

        val edge = 8 * density
        val size = ((cursor[3] - cursor[1]) / 1.2f).coerceIn(12 * density, 28 * density)
        val y = (cursor[1] + cursor[3]) / 2
        val tail = before.substring(maxOf(0, before.length - chars))
        val out = ArrayList<FxLetter>()
        // Walk right to left from the cursor
        var right = cursor[0]
        var offset = tail.length
        while (offset > 0) {
            val cp = tail.codePointBefore(offset)
            offset -= Character.charCount(cp)
            val textIndex = selStart - (tail.length - offset)
            var x = right - size * 0.28f
            var w = size * 0.55f
            var cy = y
            if (textIndex >= 0) {
                try {
                    val r: RectF? = info.getCharacterBounds(textIndex)
                    if (r != null && r.width() > 0) {
                        val b = toRoot(floatArrayOf(r.left, r.top, r.right, r.bottom), m, root)
                        x = (b[0] + b[2]) / 2
                        w = maxOf(4 * density, b[2] - b[0])
                        cy = (b[1] + b[3]) / 2
                    }
                } catch (t: Throwable) {
                    // No bounds for this one: keep the estimate
                }
            }
            right = x - w / 2
            if (cp == '\n'.code || (cp == ' '.code && tail.length > 1)) continue
            val ch = if (cp == ' '.code) "·" else String(Character.toChars(cp))
            out.add(FxLetter(ch, x.coerceIn(edge, maxOf(edge, root.width - edge)), maxOf(edge, cy), w, size))
        }
        out.reverse()
        return out.toTypedArray()
    }

    private val rootLoc = IntArray(2)

    /** Maps points from the app's text coordinates to [root]'s, through the screen. */
    private fun toRoot(pts: FloatArray, m: Matrix, root: View): FloatArray {
        m.mapPoints(pts)
        root.getLocationOnScreen(rootLoc)
        for (i in pts.indices step 2) {
            pts[i] -= rootLoc[0]
            pts[i + 1] -= rootLoc[1]
        }
        return pts
    }
}
