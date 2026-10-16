package com.yinwei.v2v.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.content.edit
import com.yinwei.v2v.languages.uiText
import com.yinwei.v2v.R
import com.yinwei.v2v.session.SessionSnapshot
import com.yinwei.v2v.settings.PresentationSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/** Service-owned caption and floating controls; compact steppers retain focus while values change. */
class CaptionOverlay(private val context: Context, private val open: () -> Unit, private val stop: () -> Unit,
    private val pause: () -> Unit, private val failed: (String) -> Unit,
    private val savePresentation: (PresentationSettings) -> Unit, private val saveGain: (Float) -> Unit) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val preferences = context.getSharedPreferences("overlay-placement", Context.MODE_PRIVATE)
    private val teal = Color.rgb(115, 222, 200)
    private val muted = Color.rgb(174, 198, 205)
    private var circle: ImageView? = null
    private var caption: LinearLayout? = null
    private var text: TextView? = null
    private var visible = true
    private var controls = false
    private var controlTab = 0
    private var reading = false
    private var paused = false
    private var appearance = PresentationSettings()
    private var translationGain = 1f
    private var latest = context.uiText("等待下一段翻譯…")
    private val refreshers = mutableListOf<() -> Unit>()
    fun setTranslationGain(gain: Float) { translationGain = gain; refreshValues() }
    fun configure(settings: PresentationSettings) { appearance = settings; applyAppearance(); refreshValues() }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 16) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun params(width: Int, height: Int, x: Int, y: Int) = WindowManager.LayoutParams(width, height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
    private fun preferredWidth() = preferences.getInt("captionWidth", dp(340)).coerceIn(dp(180), (context.resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(180)))
    fun show() {
        if (!Settings.canDrawOverlays(context)) { failed(context.uiText("請先開啟「顯示在其他應用程式上層」權限")); return }
        if (circle != null) { visible = true; render(); return }
        try {
            val bubble = ImageView(context).apply {
                setImageResource(R.drawable.ic_captions); setColorFilter(Color.WHITE); setPadding(dp(14), dp(14), dp(14), dp(14))
                background = bg(Color.rgb(8, 127, 121), 28); elevation = dp(6).toFloat()
                contentDescription = context.uiText("V2V 浮動控制：點一下展開，拖曳移動")
            }
            val bp = params(dp(56), dp(56), preferences.getInt("bubbleX", 0), preferences.getInt("bubbleY", dp(160)))
            clamp(bp, dp(56)); drag(bubble, bp, "bubble") { controls = !controls; visible = true; render() }
            manager.addView(bubble, bp); circle = bubble
            val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); elevation = dp(4).toFloat() }
            val label = OutlineCaption(context).apply {
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                contentDescription = context.uiText("翻譯字幕：拖曳移動，點一下展開控制")
            }
            panel.addView(label); text = label
            val cp = params(preferredWidth(), WindowManager.LayoutParams.WRAP_CONTENT, preferences.getInt("captionX", dp(12)), preferences.getInt("captionY", dp(280)))
            clamp(cp, dp(180)); drag(label, cp, "caption", panel) { controls = !controls; render() }
            manager.addView(panel, cp); caption = panel
            render(); failed("")
        } catch (_: Exception) { close(); failed(context.uiText("浮窗無法顯示；主畫面字幕仍可使用，請檢查上層顯示權限")) }
    }
    fun update(state: SessionSnapshot) {
        if (circle != null && !Settings.canDrawOverlays(context)) { close(); failed(context.uiText("上層顯示權限已撤銷；主畫面字幕持續")); return }
        val translated = state.segments.lastOrNull { it.translatedText.isNotBlank() }?.translatedText
        latest = when {
            state.providerRecovering -> context.uiText("服務暫時中斷，正在恢復…") + (translated?.let { "\n$it" } ?: "")
            state.providerError.isNotBlank() -> context.uiText("翻譯已暫停：%s").format(state.providerError)
            !state.cloudEnabled -> context.uiText("音訊測試模式，未啟用翻譯")
            else -> translated ?: context.uiText("等待手機播放音訊與翻譯…")
        }
        text?.text = latest
        if (paused != state.outputPaused) { paused = state.outputPaused; refreshValues() }
        listOfNotNull(circle, caption).forEach { view ->
            val p = view.layoutParams as WindowManager.LayoutParams
            clamp(p, view.height.coerceAtLeast(dp(56)))
            runCatching { manager.updateViewLayout(view, p) }.onFailure { close(); failed(context.uiText("浮窗已關閉；請重新檢查顯示權限")) }
        }
    }
    private fun present(next: PresentationSettings) { appearance = next; savePresentation(next); applyAppearance(); refreshValues() }
    private fun gain(next: Float) { translationGain = next.coerceIn(0f, 4f); saveGain(translationGain); refreshValues() }
    private fun refreshValues() { refreshers.toList().forEach { it() } }
    private fun applyAppearance() {
        val panel = caption ?: return
        panel.visibility = if (visible) View.VISIBLE else View.GONE
        panel.background = bg(Color.argb((appearance.backgroundOpacity * 255).roundToInt(), 0, 0, 0))
        text?.text = latest; text?.textSize = appearance.captionSize; text?.maxLines = appearance.captionLines
        val p = panel.layoutParams as? WindowManager.LayoutParams ?: return
        p.width = if (controls) maxOf(preferredWidth(), minOf(dp(300), context.resources.displayMetrics.widthPixels - dp(24))) else preferredWidth()
        p.alpha = if (reading && !controls && android.os.Build.VERSION.SDK_INT >= 31)
            context.getSystemService(android.hardware.input.InputManager::class.java).maximumObscuringOpacityForTouch.coerceAtMost(0.8f) else 1f
        p.flags = if (reading && !controls) p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        clamp(p, panel.height.coerceAtLeast(dp(180)))
        runCatching { manager.updateViewLayout(panel, p) }
    }
    private fun render() {
        val panel = caption ?: return
        refreshers.clear()
        while (panel.childCount > 1) panel.removeViewAt(1)
        applyAppearance()
        if (!controls) return
        val shell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)); background = bg(Color.rgb(20, 47, 54), 20)
        }
        panel.addView(shell, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val tabs = LinearLayout(context)
        listOf(context.uiText("播報"), context.uiText("字幕")).forEachIndexed { index, title ->
            tabs.addView(TextView(context).apply {
                text = title; textSize = 14f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                setTextColor(if (controlTab == index) teal else muted)
                background = bg(if (controlTab == index) 0xFF284B50.toInt() else Color.TRANSPARENT, 12)
                setOnClickListener { controlTab = index; render() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        tabs.addView(iconButton(R.drawable.ic_close, context.uiText("收合控制")) { controls = false; render() })
        shell.addView(tabs)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { isFillViewport = false; addView(list) }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, minOf(dp(if (controlTab == 0) 276 else 288), context.resources.displayMetrics.heightPixels / 3)))
        if (controlTab == 0) {
            stepper(list, context.uiText("語速"), R.drawable.ic_speed, { "%.2f×".format(appearance.speechRate) },
                { appearance.speechRate > 0.75f }, { appearance.speechRate < 2f },
                { present(appearance.copy(speechRate = (appearance.speechRate - 0.1f).coerceAtLeast(0.75f))) },
                { present(appearance.copy(speechRate = (appearance.speechRate + 0.1f).coerceAtMost(2f))) })
            stepper(list, context.uiText("增益"), R.drawable.ic_sound, { "${(translationGain * 100).roundToInt()}%" }, { translationGain > 0f }, { translationGain < 4f },
                { gain(translationGain - 0.1f) }, { gain(translationGain + 0.1f) })
            toggle(list, context.uiText("播放翻譯"), { appearance.speechEnabled }) { present(appearance.copy(speechEnabled = it)) }
            toggle(list, context.uiText("響度增強"), { appearance.enhanceSpeech }) { present(appearance.copy(enhanceSpeech = it)) }
            toggle(list, context.uiText("自動追趕"), { appearance.autoCatchUp }) { present(appearance.copy(autoCatchUp = it)) }
        } else {
            stepper(list, context.uiText("字級"), R.drawable.ic_captions, { "${appearance.captionSize.roundToInt()}sp" }, { appearance.captionSize > 6 }, { appearance.captionSize < 30 },
                { present(appearance.copy(captionSize = (appearance.captionSize - 1).coerceAtLeast(6f))) }, { present(appearance.copy(captionSize = (appearance.captionSize + 1).coerceAtMost(30f))) })
            stepper(list, context.uiText("行數"), R.drawable.ic_file, { "${appearance.captionLines}" }, { appearance.captionLines > 2 }, { appearance.captionLines < 10 },
                { present(appearance.copy(captionLines = appearance.captionLines - 1)) }, { present(appearance.copy(captionLines = appearance.captionLines + 1)) })
            stepper(list, context.uiText("背景"), R.drawable.ic_settings, { "${(appearance.backgroundOpacity * 100).roundToInt()}%" }, { appearance.backgroundOpacity > 0 }, { appearance.backgroundOpacity < 1 },
                { present(appearance.copy(backgroundOpacity = (appearance.backgroundOpacity - 0.1f).coerceAtLeast(0f))) }, { present(appearance.copy(backgroundOpacity = (appearance.backgroundOpacity + 0.1f).coerceAtMost(1f))) })
            stepper(list, context.uiText("寬度"), R.drawable.ic_width, { "${(preferredWidth() / context.resources.displayMetrics.density).roundToInt()}" }, { preferredWidth() > dp(180) }, { preferredWidth() < context.resources.displayMetrics.widthPixels - dp(24) },
                { resize(-dp(32)) }, { resize(dp(32)) })
            toggle(list, context.uiText("觸控穿透"), { reading }) { reading = it; controls = false; render() }
        }
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER }
        footer.addView(iconButton(R.drawable.ic_home, context.uiText("開啟主畫面"), open), LinearLayout.LayoutParams(0, dp(48), 1f))
        footer.addView(iconButton(R.drawable.ic_eye, context.uiText("隱藏字幕")) { visible = false; controls = false; render() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        val pauseButton = iconButton(R.drawable.ic_pause, context.uiText("暫停播報"), pause)
        refreshers += { pauseButton.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause); pauseButton.contentDescription = if (paused) context.uiText("恢復播報") else context.uiText("暫停播報") }
        footer.addView(pauseButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        footer.addView(iconButton(R.drawable.ic_stop, context.uiText("結束翻譯"), stop).apply { setColorFilter(Color.rgb(255, 174, 161)) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        shell.addView(footer); refreshValues()
    }
    private fun label(title: String) = TextView(context).apply { text = title; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER_VERTICAL }
    private fun iconButton(icon: Int, description: String, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon); setColorFilter(teal); background = bg(Color.TRANSPARENT, 12)
        setPadding(dp(12), dp(12), dp(12), dp(12)); contentDescription = description
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)); setOnClickListener { action() }
    }
    private fun stepper(parent: LinearLayout, title: String, icon: Int, value: () -> String, less: () -> Boolean, more: () -> Boolean,
        decrease: () -> Unit, increase: () -> Unit) {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(context).apply { setImageResource(icon); setColorFilter(muted) }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        row.addView(label(title), LinearLayout.LayoutParams(0, dp(52), 1f))
        val minus = iconButton(R.drawable.ic_minus, context.uiText("降低 %s").format(title), decrease)
        val number = label("").apply { gravity = Gravity.CENTER; setTextColor(teal); setTypeface(null, Typeface.BOLD); textSize = 16f }
        val plus = iconButton(R.drawable.ic_plus, context.uiText("提高 %s").format(title), increase)
        row.addView(minus); row.addView(number, LinearLayout.LayoutParams(dp(58), dp(52))); row.addView(plus); parent.addView(row)
        refreshers += { number.text = value(); minus.isEnabled = less(); plus.isEnabled = more(); minus.alpha = if (less()) 1f else 0.3f; plus.alpha = if (more()) 1f else 0.3f }
    }
    private fun toggle(parent: LinearLayout, title: String, checked: () -> Boolean, change: (Boolean) -> Unit) {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(title), LinearLayout.LayoutParams(0, dp(52), 1f))
        val switch = Switch(context).apply { contentDescription = title; minHeight = dp(48); minWidth = dp(48); thumbTintList = android.content.res.ColorStateList.valueOf(teal) }
        var refreshing = false
        switch.setOnCheckedChangeListener { _, value -> if (!refreshing) change(value) }
        row.addView(switch); parent.addView(row)
        refreshers += { refreshing = true; switch.isChecked = checked(); refreshing = false }
    }
    private fun resize(delta: Int) {
        val width = (preferredWidth() + delta).coerceIn(dp(180), (context.resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(180)))
        preferences.edit { putInt("captionWidth", width) }; applyAppearance(); refreshValues()
    }
    private fun clamp(p: WindowManager.LayoutParams, height: Int) {
        val metrics = context.resources.displayMetrics
        p.x = p.x.coerceIn(0, (metrics.widthPixels - p.width.coerceAtLeast(dp(56))).coerceAtLeast(0))
        p.y = p.y.coerceIn(dp(32), (metrics.heightPixels - height - dp(32)).coerceAtLeast(dp(32)))
    }
    private fun drag(handle: View, p: WindowManager.LayoutParams, key: String, target: View = handle, click: () -> Unit) {
        var x = 0f; var y = 0f; var oldX = 0; var oldY = 0; var moved = false
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x = event.rawX; y = event.rawY; oldX = p.x; oldY = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - x) + abs(event.rawY - y) > dp(8)) moved = true
                    p.x = oldX + (event.rawX - x).toInt(); p.y = oldY + (event.rawY - y).toInt(); clamp(p, target.height.coerceAtLeast(dp(56)))
                    runCatching { manager.updateViewLayout(target, p) }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        if (key == "bubble") p.x = if (p.x < context.resources.displayMetrics.widthPixels / 2) 0 else context.resources.displayMetrics.widthPixels - p.width
                        clamp(p, target.height.coerceAtLeast(dp(56))); runCatching { manager.updateViewLayout(target, p) }
                        preferences.edit { putInt("${key}X", p.x); putInt("${key}Y", p.y) }
                    } else { view.performClick(); click() }
                }
            }
            true
        }
    }
    fun close() { listOfNotNull(circle, caption).forEach { runCatching { manager.removeViewImmediate(it) } }; circle = null; caption = null; text = null; refreshers.clear() }
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
