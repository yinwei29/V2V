package com.yinwei.v2v.overlay

import com.yinwei.v2v.settings.PresentationSettings
import androidx.core.content.edit
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.*
import android.widget.*
import com.yinwei.v2v.session.SessionSnapshot
import kotlin.math.abs

/** Two small service-owned windows. No full-screen touch interception. */
class CaptionOverlay(private val context: Context, private val open: () -> Unit,
    private val stop: () -> Unit, private val pause: () -> Unit, private val failed: (String) -> Unit) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val preferences = context.getSharedPreferences("overlay-placement", Context.MODE_PRIVATE)
    private var circle: TextView? = null
    private var caption: LinearLayout? = null
    private var text: TextView? = null
    private var visible = true
    private var controls = false
    private var appearance = PresentationSettings()
    private var reading = false
    fun configure(settings: PresentationSettings) { appearance = settings; render() }
    private var latest = "等待下一段翻譯…"
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun params(width: Int, height: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
    fun show() {
        if (!Settings.canDrawOverlays(context)) { failed("浮窗尚未授權；請在主畫面開啟「顯示在其他應用程式上層」"); return }
        if (circle != null) { visible = true; render(); return }
        try {
            val bubble = TextView(context).apply {
                text = "V2V"; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(0, 105, 98)) }
                contentDescription = "V2V 浮動控制，點一下展開，拖曳移動"
            }
            val bp = params(dp(56), dp(56), preferences.getInt("bubbleX", 0), preferences.getInt("bubbleY", dp(160)))
            clamp(bp); drag(bubble, bp, "bubble") { controls = !controls; visible = true; render() }
            manager.addView(bubble, bp); circle = bubble
            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply { setColor(0x8c000000.toInt()); cornerRadius = dp(12).toFloat() }
            }
            val label = OutlineCaption(context).apply {
                setTextColor(Color.WHITE); textSize = 18f; setTypeface(null, Typeface.BOLD)
                maxLines = 4
                contentDescription = "翻譯字幕；拖曳可移動，點一下展開控制"
            }
            panel.addView(label); text = label
            val cp = params(preferences.getInt("captionWidth", dp(340)).coerceIn(dp(180), (context.resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(180))), WindowManager.LayoutParams.WRAP_CONTENT,
                preferences.getInt("captionX", dp(12)), preferences.getInt("captionY", dp(300)))
            clamp(cp); drag(label, cp, "caption", panel) { controls = !controls; render() }
            manager.addView(panel, cp); caption = panel
            render(); failed("")
        } catch (_: Exception) { close(); failed("無法顯示浮窗；請檢查上層顯示權限，主畫面字幕仍可使用") }
    }
    fun update(state: SessionSnapshot) {
        if (circle != null && !Settings.canDrawOverlays(context)) { close(); failed("上層顯示權限已撤銷；主畫面字幕持續"); return }
        latest = state.segments.lastOrNull { it.translatedText.isNotBlank() }?.translatedText ?: "等待下一段翻譯…"
        text?.text = latest
        // Rotation/display changes can otherwise leave a window outside the usable area.
        listOfNotNull(circle, caption).forEach { view ->
            val p = view.layoutParams as WindowManager.LayoutParams
            clamp(p); runCatching { manager.updateViewLayout(view, p) }.onFailure { close(); failed("浮窗已關閉；請重新檢查顯示權限") }
        }
    }
    private fun render() {
        val panel = caption ?: return
        panel.visibility = if (visible) View.VISIBLE else View.GONE
        text?.text = latest
        text?.textSize = appearance.captionSize
        text?.maxLines = appearance.captionLines
        (panel.background as? GradientDrawable)?.setColor(Color.argb((appearance.backgroundOpacity * 255).toInt(), 0, 0, 0))
        val layout = panel.layoutParams as? WindowManager.LayoutParams
        if (layout != null) {
            // Android 12+ only passes touches through untrusted overlay windows below its opacity limit.
            layout.alpha = if (reading && !controls && android.os.Build.VERSION.SDK_INT >= 31)
                context.getSystemService(android.hardware.input.InputManager::class.java).maximumObscuringOpacityForTouch.coerceAtMost(0.8f)
                else 1f
            layout.flags = if (reading && !controls) layout.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                else layout.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { manager.updateViewLayout(panel, layout) }
        }
        while (panel.childCount > 1) panel.removeViewAt(1)
        if (!controls) return
        fun button(label: String, action: () -> Unit) {
            panel.addView(Button(context).apply { text = label; setOnClickListener { action() } })
        }
        button(if (reading) "允許拖曳字幕" else "閱讀模式（觸控穿透，文字略透明）") { reading = !reading; controls = false; render() }
        button("字幕加寬") { resize(dp(40)) }
        button("字幕縮窄") { resize(-dp(40)) }
        button("暫停／恢復播報", pause)
        button("開啟主畫面", open)
        button("隱藏字幕（點 V2V 可恢復）") { visible = false; controls = false; render() }
        button("停止翻譯", stop)
    }
    private fun resize(delta: Int) {
        val panel = caption ?: return
        val p = panel.layoutParams as WindowManager.LayoutParams
        p.width = (p.width + delta).coerceIn(dp(180), (context.resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(180)))
        clamp(p); runCatching { manager.updateViewLayout(panel, p) }
        preferences.edit { putInt("captionWidth", p.width) }
    }
    private fun clamp(p: WindowManager.LayoutParams) {
        val metrics = context.resources.displayMetrics
        p.x = p.x.coerceIn(0, (metrics.widthPixels - p.width.coerceAtLeast(dp(56))).coerceAtLeast(0))
        p.y = p.y.coerceIn(dp(32), (metrics.heightPixels - maxOf(dp(if (controls) 540 else 160), if (p.height > 0) p.height else caption?.height ?: 0) - dp(32)).coerceAtLeast(dp(32)))
    }
    private fun drag(handle: View, p: WindowManager.LayoutParams, key: String, target: View = handle, click: () -> Unit) {
        var x = 0f; var y = 0f; var oldX = 0; var oldY = 0; var moved = false
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x = event.rawX; y = event.rawY; oldX = p.x; oldY = p.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - x) + abs(event.rawY - y) > dp(8)) moved = true
                    p.x = oldX + (event.rawX - x).toInt(); p.y = oldY + (event.rawY - y).toInt(); clamp(p)
                    runCatching { manager.updateViewLayout(target, p) }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        if (key == "bubble") p.x = if (p.x < context.resources.displayMetrics.widthPixels / 2) 0 else context.resources.displayMetrics.widthPixels - p.width
                        clamp(p); runCatching { manager.updateViewLayout(target, p) }
                        preferences.edit { putInt("${key}X", p.x); putInt("${key}Y", p.y) }
                    } else { view.performClick(); click() }
                    true
                }
                else -> true
            }
        }
    }
    fun close() {
        listOfNotNull(circle, caption).forEach { runCatching { manager.removeViewImmediate(it) } }
        circle = null; caption = null; text = null
    }
}

/** Draws the same text layout twice: black outline then opaque white fill. */
private class OutlineCaption(context: Context) : androidx.appcompat.widget.AppCompatTextView(
    android.view.ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)) {
    override fun onDraw(canvas: android.graphics.Canvas) {
        val textLayout = layout ?: return
        val checkpoint = canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 1.5f * resources.displayMetrics.density
        paint.strokeJoin = android.graphics.Paint.Join.ROUND
        paint.color = Color.BLACK
        textLayout.draw(canvas)
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = Color.WHITE
        textLayout.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}
