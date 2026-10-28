// FXboard addition: runs keyfx, the effects library, on the keyboard. MainKeyboardView owns one;
// FUTO's input code reports what the user does through KeyFxHost.active. keyfx draws in the root
// view's overlay, so effects can reach the suggestion bar above the keys.
package org.futo.inputmethod.keyboard

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.InputConnection
import com.theo.keyfx.FxHost
import com.theo.keyfx.FxKey
import com.theo.keyfx.FxLayout
import com.theo.keyfx.KeyFx
import com.theo.keyfx.KeyLook
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.settings.pages.KeyFxPrefs
import java.util.IdentityHashMap

class KeyFxHost(private val view: MainKeyboardView) : FxHost {
    companion object {
        /** The host of the keyboard on screen, for input code that has no view at hand. */
        @JvmStatic
        var active: KeyFxHost? = null
            private set

        /** Whether the field being typed in is a password field, where keyfx stays off. */
        @JvmStatic
        var privateField = false
            set(value) {
                field = value
                active?.fx?.setPrivateField(value)
            }

        // Touch points kept while waiting to learn whether a touch is a swipe
        private const val SWIPE_BUFFER_MAX = 400
        // How long the show animation may keep the keyboard hidden before it comes back anyway
        private const val SHOW_SAFETY_MS = 2500L
    }

    private val prefs: SharedPreferences = KeyFxPrefs.open(view.context)
    private val density = view.resources.displayMetrics.density
    private val fx = KeyFx(density, prefs, KeyFxPrefs.CAPS)

    // Fields, not locals: SharedPreferences only holds its listeners weakly
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> fx.reload() }
    private val rootLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateLayout() }

    private var root: View? = null
    private var keyboard: Keyboard? = null
    private val fxKeys = IdentityHashMap<Key, FxKey>()
    private val loc = IntArray(2)
    private val rootLoc = IntArray(2)
    // Where the keyboard view sits in the root view, whose coordinates keyfx uses
    private var offX = 0f
    private var offY = 0f
    // What the current keyfx layout was built from, to skip rebuilding when nothing changed
    private var builtKeyboard: Keyboard? = null
    private var builtOffX = 0f
    private var builtOffY = 0f
    private var builtWidth = 0
    private var builtHeight = 0
    private var showSafety: Runnable? = null

    private val swipeBuffer = ArrayList<FloatArray>()
    private var swipeBase = 0L
    private var downX = 0f
    private var downY = 0f
    private var deleteBefore: String? = null
    private var deleteAnchor: CursorAnchorInfo? = null
    private var deleteHolding = false
    private var cursorOn = false

    init {
        fx.setHost(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    // region View lifecycle and layout

    fun attach() {
        val r = view.rootView
        if (root !== r) {
            detachRoot()
            root = r
            r.overlay.add(fx)
            r.addOnLayoutChangeListener(rootLayoutListener)
        }
        active = this
        fx.setEnabled(animationsOn())
        fx.setPrivateField(privateField)
        updateLayout()
    }

    /** Effects follow Android's "Remove animations" setting, readable from Android 8 on. */
    private fun animationsOn(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    fun detach() {
        detachRoot()
        fx.reset()
        if (active === this) active = null
    }

    private fun detachRoot() {
        root?.overlay?.remove(fx)
        root?.removeOnLayoutChangeListener(rootLayoutListener)
        root = null
    }

    fun setKeyboard(keyboard: Keyboard, accent: Int) {
        this.keyboard = keyboard
        fx.setSystemAccent(accent)
        // No fx.reset() here: this runs on every shift change and would end a running swipe word
        updateLayout()
    }

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** Rebuilds keyfx's layout from FUTO's keys, in root view coordinates. */
    fun updateLayout() {
        val r = root ?: return
        fx.setBounds(0, 0, r.width, r.height)
        view.getLocationInWindow(loc)
        r.getLocationInWindow(rootLoc)
        offX = (loc[0] - rootLoc[0]).toFloat()
        offY = (loc[1] - rootLoc[1]).toFloat()
        val kb = keyboard ?: return
        if (kb === builtKeyboard && offX == builtOffX && offY == builtOffY &&
            view.width == builtWidth && view.height == builtHeight) return
        builtKeyboard = kb
        builtOffX = offX
        builtOffY = offY
        builtWidth = view.width
        builtHeight = view.height

        // Keys that didn't move keep their FxKey, so effects running on them carry on
        val old = HashMap<Bounds, FxKey>()
        for (k in fx.layout().keys) old[Bounds(k.left, k.top, k.right, k.bottom)] = k
        fxKeys.clear()
        val keys = ArrayList<FxKey>()
        val padLeft = view.paddingLeft + offX
        val padTop = view.paddingTop + offY
        for (key in kb.sortedKeys) {
            val left = padLeft + key.drawX
            val top = padTop + key.y
            val right = left + key.drawWidth
            val bottom = top + key.height
            val k = old.remove(Bounds(left, top, right, bottom)) ?: FxKey().also {
                it.left = left
                it.top = top
                it.right = right
                it.bottom = bottom
            }
            k.code = key.code
            k.label = key.label
            k.type = typeOf(key.code)
            k.longPress = key.isLongPressEnabled
            keys.add(k)
            fxKeys[key] = k
        }
        val keyboardRect = RectF(offX, offY, offX + view.width, offY + view.height)
        // FUTO's suggestion bar sits right above the keys; the window is full height, so cap it
        val barHeight = 48 * density
        val bar = if (offY >= barHeight * 0.8f) RectF(offX, maxOf(0f, offY - barHeight), keyboardRect.right, offY) else RectF()
        fx.setLayout(FxLayout(keys.toTypedArray(), keyboardRect, bar))
    }

    private fun typeOf(code: Int): Int = when (code) {
        Constants.CODE_SPACE -> FxKey.SPACE
        Constants.CODE_ENTER -> FxKey.ENTER
        Constants.CODE_DELETE -> FxKey.DELETE
        Constants.CODE_SHIFT -> FxKey.SHIFT
        in '0'.code..'9'.code -> FxKey.NUM
        else -> if (code > Constants.CODE_SPACE) FxKey.CHAR else FxKey.OTHER
    }

    /** The key that types this code, nearest where the finger went down; capitals match their letter. */
    private fun keyFor(code: Int): FxKey? {
        val lower = if (Character.isValidCodePoint(code)) Character.toLowerCase(code) else code
        var best: FxKey? = null
        var bestDist = Float.MAX_VALUE
        for (k in fx.layout().keys) {
            if (k.code != code && k.code != lower) continue
            val dx = k.cx() - downX
            val dy = k.cy() - downY
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = k
            }
        }
        return best
    }

    // endregion

    // region Real keys (CAP_KEY_TRANSFORM)

    /** How KeyboardView should draw this key now; false when it draws as normal. */
    fun look(key: Key, out: KeyLook): Boolean {
        val k = fxKeys[key] ?: return false
        return fx.look(k, out)
    }

    override fun redrawKeys() {
        view.invalidateAllKeys()
    }

    override fun wordChanged() {
        // The suggestion bar doesn't read word() yet (CAP_SUGGESTION_STYLE comes in a later update)
    }

    // endregion

    // region Events

    /** Every touch on the keyboard view, before FUTO handles it. Never changes the event. */
    fun onTouch(e: MotionEvent) {
        // FUTO's hit boxes include the gaps between keys
        val slop = 4 * density
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Compose can move the view without a relayout; cheap when nothing changed
                updateLayout()
                downX = e.x + offX
                downY = e.y + offY
                swipeBuffer.clear()
                swipeBase = e.eventTime
                swipeBuffer.add(floatArrayOf(downX, downY, 0f))
                fx.keyDown(fx.layout().hit(downX, downY, slop), downX, downY)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i) + offX
                val y = e.getY(i) + offY
                // Rollover typing: keys typed now belong to this finger
                downX = x
                downY = y
                fx.keyDown(fx.layout().hit(x, y, slop), x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // FUTO skips releaseKey after key repeats, so end a delete hold here too
                if (deleteHolding) {
                    deleteHolding = false
                    fx.deleteHoldEnd()
                }
                cursorEnd()
            }
            MotionEvent.ACTION_MOVE -> {
                val x = e.x + offX
                val y = e.y + offY
                if (fx.swiping()) {
                    fx.swipePoint(x, y, e.eventTime)
                } else if (swipeBuffer.size < SWIPE_BUFFER_MAX) {
                    swipeBuffer.add(floatArrayOf(x, y, (e.eventTime - swipeBase).toFloat()))
                }
            }
        }
    }

    /** FUTO recognized a swipe: start it in keyfx and replay the points since touch-down. */
    fun swipeStart() {
        fx.longPressEnd()
        fx.swipeStart()
        for (p in swipeBuffer) fx.swipePoint(p[0], p[1], swipeBase + p[2].toLong())
        swipeBuffer.clear()
    }

    fun swipeEnd() = fx.swipeEnd()

    fun swipeCancel() = fx.swipeCancel()

    // Called from FUTO's background suggestion thread
    fun swipeGuess(word: String?) { view.post { fx.swipeGuess(word) } }

    fun swipeWord(word: String?) = fx.swipeWord(word)

    /** Whether any backspace effect is on, so the app's text is only read when it is needed. */
    private fun deleteFxOn() = fx.isEnabled && (fx.on("del") || fx.on("delHold"))

    /** Just before FUTO handles a key: backspace remembers the text it is about to change. */
    fun beforeCodeInput(code: Int, ic: InputConnection?) {
        if (code == Constants.CODE_DELETE) {
            deleteBefore = if (deleteFxOn()) FxText.textBefore(ic) else null
            // The app reports the cursor after it changes, so this still describes the text before the delete
            deleteAnchor = FxText.anchor
        }
    }

    /** Just after FUTO handled a key. Backspace's first press is a tap; its repeats are the hold. */
    fun afterCodeInput(code: Int, isKeyRepeat: Boolean, ic: InputConnection?) {
        val k = keyFor(code) ?: return
        if (code == Constants.CODE_DELETE) {
            val before = deleteBefore
            deleteBefore = null
            if (!deleteFxOn()) return
            val chars = FxText.deletedLength(before, FxText.textBefore(ic))
            val kb = fx.layout().keyboard
            val letters = FxText.lettersAt(deleteAnchor, before, chars, root, density, kb.centerX(), kb.top)
            deleteAnchor = null
            if (!isKeyRepeat) {
                fx.deleteTap(k, *letters)
            } else {
                if (!deleteHolding) {
                    deleteHolding = true
                    fx.deleteHoldStart(k)
                }
                // Right to left, the order they were deleted in
                for (i in letters.indices.reversed()) fx.deleteHoldLetter(letters[i])
            }
            return
        }
        if (isKeyRepeat) return
        val glyph = if (code > Constants.CODE_SPACE && Character.isValidCodePoint(code)) String(Character.toChars(code)) else null
        fx.keyTyped(k, glyph, if (k.type == FxKey.SPACE) downX else k.cx())
    }

    fun releaseKey(code: Int) {
        if (code == Constants.CODE_DELETE && deleteHolding) {
            deleteHolding = false
            fx.deleteHoldEnd()
        }
    }

    fun longPressStart(key: Key, delayMs: Int) {
        val k = fxKeys[key] ?: return
        if (k.type == FxKey.DELETE || k.type == FxKey.SHIFT) return
        fx.longPressStart(k, if (k.type == FxKey.SPACE) downX else Float.NaN, delayMs.toLong())
    }

    fun longPressEnd() = fx.longPressEnd()

    /** The spacebar is moving the cursor; x and y are keyboard view coordinates. */
    fun cursorAt(x: Int, y: Int) {
        val rx = x + offX
        val ry = y + offY
        if (!cursorOn) {
            cursorOn = true
            fx.cursorMode(true, rx, ry)
        }
        fx.cursorMove(rx, ry)
    }

    fun cursorEnd() {
        if (!cursorOn) return
        cursorOn = false
        fx.cursorMode(false, 0f, 0f)
    }

    /** The keyboard appeared: play the show animation, from a snapshot unless keys animate themselves. */
    fun show() {
        view.post {
            fx.setEnabled(animationsOn())
            if (!view.isShown || view.width == 0 || view.height == 0 || !fx.isEnabled) return@post
            if ("none" == fx.settings.s("show", "mode")) return@post
            updateLayout()
            if (!fx.showNeedsSnapshot()) {
                fx.show(null, null)
                return@post
            }
            val snapshot = try {
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                    // Otherwise KeyboardView's software draw reuses a stale offscreen buffer
                    view.invalidateAllKeys()
                    view.draw(Canvas(it))
                }
            } catch (t: Throwable) {
                return@post // out of memory or a draw failure: just show the keyboard normally
            }
            val restore = Runnable { view.alpha = 1f }
            view.alpha = 0f
            if (!fx.show(snapshot, restore)) {
                view.alpha = 1f
                return@post
            }
            // Safety net: the keys come back even if the animation never finishes
            showSafety?.let { view.removeCallbacks(it) }
            showSafety = restore
            view.postDelayed(restore, SHOW_SAFETY_MS)
        }
    }

    fun hidden() {
        fx.reset()
        cursorOn = false
        deleteHolding = false
        view.alpha = 1f // never leave the keys hidden by an interrupted show
        showSafety?.let { view.removeCallbacks(it) }
        showSafety = null
    }

    // endregion
}
