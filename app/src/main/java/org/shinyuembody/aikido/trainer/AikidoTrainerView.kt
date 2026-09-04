package org.shinyuembody.aikido.trainer

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Native 3D movement trainer embedded inside the Compose Shinyu app.
 * Buttons are the primary controls; gesture recognition remains available on the dojo surface.
 */
class AikidoTrainerView(context: Context) : LinearLayout(context) {
    private lateinit var surfaceView: AikidoSurfaceView
    private val statusView: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(247, 245, 238))
        setPadding(dp(10), dp(8), dp(10), dp(8))

        val intro = TextView(context).apply {
            text = "Aikido movement trainer"
            setTextColor(Color.rgb(24, 61, 50))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        addView(intro)

        addView(TextView(context).apply {
            text = "Test each movement directly. Gestures also work on the dojo."
            setTextColor(Color.rgb(98, 104, 100))
            textSize = 12.5f
            setPadding(dp(2), 0, dp(2), dp(6))
        })

        val controls = LinearLayout(context).apply { orientation = VERTICAL }

        fun movementButton(label: String, move: AikidokaRenderer.MoveType): Button = Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 11.5f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(3), dp(2), dp(3), dp(2))
            setOnClickListener { surfaceView.performMove(move) }
        }

        fun addControlRow(vararg items: Pair<String, AikidokaRenderer.MoveType>) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                weightSum = 3f
            }
            items.forEach { (label, move) ->
                row.addView(
                    movementButton(label, move),
                    LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                )
            }
            while (row.childCount < 3) {
                row.addView(View(context), LayoutParams(0, dp(44), 1f))
            }
            controls.addView(row)
        }

        // Simple stepping first, compound turns last.
        addControlRow(
            "Okuri +" to AikidokaRenderer.MoveType.OKURI_FORWARD,
            "Ayumi +" to AikidokaRenderer.MoveType.AYUMI_FORWARD,
            "Tsugi +" to AikidokaRenderer.MoveType.TSUGI_FORWARD
        )
        addControlRow(
            "Okuri -" to AikidokaRenderer.MoveType.OKURI_BACKWARD,
            "Ayumi -" to AikidokaRenderer.MoveType.AYUMI_BACKWARD,
            "Tsugi -" to AikidokaRenderer.MoveType.TSUGI_BACKWARD
        )
        addControlRow(
            "Tenkai" to AikidokaRenderer.MoveType.TENKAI,
            "Tenkan" to AikidokaRenderer.MoveType.TENKAN,
            "Irimi tenkan" to AikidokaRenderer.MoveType.IRIMI_TENKAN
        )
        addView(controls)

        val stage = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(14, 31, 26))
        }
        val trail = GestureTrailView(context)
        statusView = TextView(context).apply {
            text = "Ready • Right leg forward • facing viewer"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(190, 9, 22, 18))
            textSize = 13.5f
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(7), dp(9), dp(7))
        }

        surfaceView = AikidoSurfaceView(
            context,
            onStatus = { text -> post { statusView.text = text } },
            onAnimationComplete = { move -> post { statusView.text = surfaceView.completionSummary(move) } }
        )
        surfaceView.trailView = trail

        stage.addView(
            surfaceView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        stage.addView(
            trail,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        stage.addView(
            statusView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(dp(10), dp(10), dp(10), dp(10))
            }
        )
        addView(
            stage,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(8)
                bottomMargin = dp(6)
            }
        )

        val reset = Button(context).apply {
            text = "Reset stance"
            isAllCaps = false
            setOnClickListener { surfaceView.resetCharacter() }
        }
        addView(reset, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
    }

    fun onHostResume() = surfaceView.onResume()
    fun onHostPause() = surfaceView.onPause()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
