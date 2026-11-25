package com.cchigu.distraction_killa

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher

/** Full-screen "blocked" page shown over a blocked app or site. Closing it goes home. */
class BlockActivity : Activity() {

    companion object {
        const val EXTRA_WHAT = "what"
        const val EXTRA_MESSAGE = "message" // optional full sentence instead of "<what> is blocked…"
        private const val BG = 0xFF0D0F12.toInt()
        private const val TEXT = 0xFFF2F5F8.toInt()
        private const val MUTED = 0xFF8A939E.toInt()
        private const val ACCENT = 0xFF5BC0F8.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(BG)
        setContentView(buildView(messageOf(intent)))

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { leave() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setContentView(buildView(messageOf(intent)))
    }

    private fun messageOf(i: Intent): String =
        i.getStringExtra(EXTRA_MESSAGE) ?: "${i.getStringExtra(EXTRA_WHAT) ?: "This"} is blocked until 9 AM"

    @Deprecated("Pre-Android 13 back handling")
    override fun onBackPressed() = leave()

    private fun leave() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildView(message: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(BG)
        setPadding(dp(32), dp(32), dp(32), dp(32))

        addView(TextView(context).apply {
            text = "Killed."
            textSize = 48f
            setTextColor(TEXT)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = message
            textSize = 17f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(40))
        })
        addView(TextView(context).apply {
            text = "Close"
            textSize = 17f
            setTextColor(0xFF000000.toInt())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(ACCENT)
                cornerRadius = dp(28).toFloat()
            }
            setOnClickListener { leave() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
    }
}
