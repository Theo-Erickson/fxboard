// Fork addition (FXboard): the "Select" button shown while the spacebar trackpad moves the cursor.
// PointerTracker decides when it shows and what touching it does; this class only places and
// draws it. It draws in the root view's overlay, added after keyfx's, so the trackpad's dimming
// never covers it.

package org.futo.inputmethod.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import org.futo.inputmethod.latin.R

class TrackpadSelectButton(private val view: MainKeyboardView) : Drawable() {
    companion object {
        /** The button of the keyboard on screen, for PointerTracker, which has no view at hand. */
        @JvmStatic
        var active: TrackpadSelectButton? = null
            private set

        private const val FADE_MS = 120f
    }

    private val density = view.resources.displayMetrics.density
    private val labelSelect = view.resources.getString(R.string.fork_trackpad_select)
    private val labelSelecting = view.resources.getString(R.string.fork_trackpad_selecting)
    private var keyboard: Keyboard? = null
    private var accent = Color.WHITE

    private var root: View? = null
    private val loc = IntArray(2)
    private val rootLoc = IntArray(2)
    private var offX = 0f
    private var offY = 0f

    // Where the button is, in keyboard view coordinates (the ones PointerTracker sees)
    private val hit = RectF()
    private val drawn = RectF()
    private var showing = false
    private var selecting = false
    private var shownAt = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun attach() {
        active = this
    }

    fun detach() {
        hide()
        if (active === this) active = null
    }

    fun setKeyboard(keyboard: Keyboard, accent: Int) {
        this.keyboard = keyboard
        this.accent = accent
    }

    val isShowing: Boolean get() = showing

    /** True when (x, y), in keyboard view coordinates, is on the button. */
    fun contains(x: Int, y: Int): Boolean = showing && hit.contains(x.toFloat(), y.toFloat())

    fun show() {
        if (showing) return
        if (!place()) return
        val r = view.rootView ?: return
        root = r
        setBounds(0, 0, r.width, r.height)
        r.overlay.add(this)
        showing = true
        selecting = false
        shownAt = SystemClock.uptimeMillis()
        invalidateSelf()
    }

    fun setSelecting(on: Boolean) {
        if (selecting == on) return
        selecting = on
        invalidateSelf()
    }

    fun hide() {
        if (!showing) return
        showing = false
        selecting = false
        root?.overlay?.remove(this)
        root = null
    }

    /** Puts the button on the keyboard's bottom-right key (usually Enter). */
    private fun place(): Boolean {
        val kb = keyboard ?: return false
        var best: Key? = null
        for (key in kb.sortedKeys) {
            if (key.isSpacer || key.width <= 0 || key.height <= 0) continue
            val b = best
            val bottom = key.y + key.height
            val bestBottom = if (b == null) Int.MIN_VALUE else b.y + b.height
            // Keys in the same row can differ by a pixel or two; treat those as level
            val tolerance = kb.mMostCommonKeyHeight / 4
            if (b == null || bottom > bestBottom + tolerance ||
                (bottom >= bestBottom - tolerance && key.drawX + key.drawWidth > b.drawX + b.drawWidth)) {
                best = key
            }
        }
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float
        val key = best
        if (key != null) {
            left = (view.paddingLeft + key.drawX).toFloat()
            top = (view.paddingTop + key.y).toFloat()
            right = left + key.drawWidth
            bottom = top + key.height
        } else {
            // No keys: a key-sized square in the corner
            val size = kb.mMostCommonKeyHeight.toFloat()
            right = (view.width - view.paddingRight).toFloat()
            bottom = (view.height - view.paddingBottom).toFloat()
            left = right - size
            top = bottom - size
        }
        hit.set(left, top, right, bottom)
        val inset = 3 * density
        drawn.set(left + inset, top + inset, right - inset, bottom - inset)

        view.getLocationInWindow(loc)
        view.rootView.getLocationInWindow(rootLoc)
        offX = (loc[0] - rootLoc[0]).toFloat()
        offY = (loc[1] - rootLoc[1]).toFloat()
        return true
    }

    override fun draw(canvas: Canvas) {
        if (!showing) return
        val fade = ((SystemClock.uptimeMillis() - shownAt) / FADE_MS).coerceIn(0f, 1f)
        val a = (fade * 255).toInt()
        val radius = 8 * density

        canvas.save()
        canvas.translate(offX, offY)

        // Selecting: solid accent. Waiting: a softer accent with an outline, so it reads as an option
        fill.color = accent
        fill.alpha = if (selecting) a else (a * 0.55f).toInt()
        canvas.drawRoundRect(drawn, radius, radius, fill)
        if (!selecting) {
            stroke.color = accent
            stroke.alpha = a
            stroke.strokeWidth = 1.5f * density
            canvas.drawRoundRect(drawn, radius, radius, stroke)
        }

        // Dark text on a light accent, light text on a dark one
        val lum = (0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)) / 255
        text.color = if (lum > 0.6) Color.BLACK else Color.WHITE
        text.alpha = a
        text.isFakeBoldText = selecting
        text.textSize = minOf(drawn.height() * 0.34f, 15 * density)
        val label = if (selecting) labelSelecting else labelSelect
        // Shrink the label to fit narrow keys
        val maxWidth = drawn.width() - 6 * density
        val width = text.measureText(label)
        if (width > maxWidth && width > 0) text.textSize *= maxWidth / width
        val baseline = drawn.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(label, drawn.centerX(), baseline, text)

        canvas.restore()
        if (fade < 1f) invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun onBoundsChange(bounds: Rect) {}
}
