package org.shinyuembody.aikido.trainer

import android.content.Context
import android.graphics.PointF
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Gesture grammar v2.9.
 *
 * Turning is bilateral and depends on the CURRENT lead leg:
 *
 * LEFT leg forward:
 *   anticlockwise circle                 -> Irimi tenkan
 *   clockwise circle                     -> Tenkai
 *   clockwise circle + backward tail     -> Tenkan
 *
 * RIGHT leg forward:
 *   clockwise circle                     -> Irimi tenkan
 *   anticlockwise circle                 -> Tenkai
 *   anticlockwise circle + backward tail -> Tenkan
 *
 * Footwork:
 *   long straight stroke toward body's front -> Okuri ashi forward
 *   long straight stroke toward body's rear  -> Okuri ashi backward
 *   horizontal stroke in top zone            -> Ayumi ashi forward
 *   horizontal stroke in bottom zone         -> Ayumi ashi backward
 *   double tap                               -> Tsugi ashi forward
 *   tap, then short stroke toward body rear  -> Tsugi ashi backward
 */
class AikidoSurfaceView(
    context: Context,
    private val onStatus: (String) -> Unit,
    onAnimationComplete: (AikidokaRenderer.MoveType) -> Unit
) : GLSurfaceView(context) {

    val aikidokaRenderer = AikidokaRenderer(onAnimationComplete)
    var trailView: GestureTrailView? = null

    private data class CircleGesture(
        val clockwise: Boolean,
        val hasBackTail: Boolean
    )

    private data class CircleStats(
        val clockwise: Boolean,
        val valid: Boolean
    )

    private val path = mutableListOf<PointF>()
    private val handler = Handler(Looper.getMainLooper())
    private var downTime = 0L

    // A single tap is remembered briefly. A second tap means Tsugi forward;
    // a following rearward stroke means Tsugi backward.
    private var pendingTapTime = 0L
    private var pendingTapX = 0f
    private var pendingTapY = 0f

    // Snapshot the state at the instant a move is accepted. This is kept
    // outside the renderer animation so the completion label can show
    // start -> finish unambiguously, even after the lead leg/facing have changed.
    @Volatile private var lastStartRightLead = true
    @Volatile private var lastStartFacingViewer = true
    @Volatile private var lastStartedMove: AikidokaRenderer.MoveType? = null

    private val density = resources.displayMetrics.density
    private val tapTolerance = 24f * density
    private val ayumiHorizontalMin = 82f * density
    private val okuriLongMin = 155f * density
    private val tsugiBackStrokeMin = 48f * density
    private val tsugiBackStrokeMax = 150f * density

    init {
        setEGLContextClientVersion(2)
        setRenderer(aikidokaRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
    }

    fun resetCharacter() {
        queueEvent { aikidokaRenderer.resetPose() }
        path.clear()
        pendingTapTime = 0L
        trailView?.clearTrail()
        onStatus("Position reset • Right leg forward • facing viewer")
    }

    /** Direct control used by the movement buttons. Gestures remain available,
     * but buttons bypass recognition so each biomechanical sequence can be
     * tested independently. */
    fun performMove(move: AikidokaRenderer.MoveType) {
        pendingTapTime = 0L
        path.clear()
        trailView?.clearTrail()
        trigger(move)
    }

    /** Human-readable endpoint trace for testing the biomechanics. */
    fun completionSummary(move: AikidokaRenderer.MoveType): String {
        val startLead = if (lastStartRightLead) "Right" else "Left"
        val endLead = if (aikidokaRenderer.isRightLead()) "Right" else "Left"
        val endFacing = aikidokaRenderer.currentFacingLabel()

        val turn = when (move) {
            AikidokaRenderer.MoveType.TENKAI, AikidokaRenderer.MoveType.TENKAN ->
                if (lastStartRightLead) "turn left" else "turn right"
            AikidokaRenderer.MoveType.IRIMI_TENKAN ->
                if (lastStartRightLead) "clockwise" else "anticlockwise"
            else -> null
        }

        val core = "${move.label}: $startLead → $endLead"
        return if (turn != null) "$core • $turn • $endFacing" else "$core • $endFacing"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                path.clear()
                path.add(PointF(event.x, event.y))
                trailView?.setPoints(path)
                downTime = event.eventTime
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val point = PointF(event.x, event.y)
                path.add(point)
                if (path.size > 180) path.removeAt(0)
                trailView?.setPoints(path)
                return true
            }

            MotionEvent.ACTION_UP -> {
                path.add(PointF(event.x, event.y))
                trailView?.setPoints(path)
                classifyGesture(event.eventTime - downTime)
                handler.postDelayed({ trailView?.clearTrail() }, 240L)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                path.clear()
                trailView?.clearTrail()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun classifyGesture(durationMs: Long) {
        if (aikidokaRenderer.isAnimating) {
            onStatus("Finish the current movement first")
            return
        }
        if (path.size < 2) return

        val start = path.first()
        val end = path.last()
        val dx = end.x - start.x
        val dy = end.y - start.y
        val chord = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val total = totalPathLength(path)

        // 1. Tap handling. This must happen before every stroke grammar because
        // the first tap arms the tap+back-stroke version of Tsugi ashi.
        val isTap = chord <= tapTolerance && total <= tapTolerance * 1.25f && durationMs < 350L
        if (isTap) {
            classifyTap(end)
            return
        }

        // 2. If a tap is armed, a short straight stroke toward the body's rear
        // is Tsugi ashi backward. It takes priority over Okuri.
        val now = System.currentTimeMillis()
        if (pendingTapTime != 0L && now - pendingTapTime <= 750L) {
            if (isStraightBodyAxis(path) && chord in tsugiBackStrokeMin..tsugiBackStrokeMax && isScreenStrokeBackward(dy)) {
                pendingTapTime = 0L
                trigger(AikidokaRenderer.MoveType.TSUGI_BACKWARD)
                return
            }
        } else {
            pendingTapTime = 0L
        }

        // 3. Circle grammar. Circle direction is read literally from the user's
        // finger, then interpreted with the current lead leg.
        detectCircleGesture(path)?.let { circle ->
            pendingTapTime = 0L
            classifyCircle(circle)
            return
        }

        // 4. Ayumi ashi: horizontal stroke in the top or bottom part of the dojo.
        // Top always means forward; bottom always means backward, regardless of facing.
        if (isStraightHorizontal(path) && chord >= ayumiHorizontalMin) {
            pendingTapTime = 0L
            val averageY = path.sumOf { it.y.toDouble() }.toFloat() / path.size
            when {
                averageY <= height * 0.43f -> trigger(AikidokaRenderer.MoveType.AYUMI_FORWARD)
                averageY >= height * 0.57f -> trigger(AikidokaRenderer.MoveType.AYUMI_BACKWARD)
                else -> onStatus("Ayumi: draw the horizontal stroke near the TOP for forward or BOTTOM for backward")
            }
            return
        }

        // 5. Okuri ashi: one long straight drag along the screen projection of
        // the body's attack line. The direction is interpreted from current facing.
        if (isStraightBodyAxis(path) && chord >= okuriLongMin) {
            pendingTapTime = 0L
            trigger(
                if (isScreenStrokeForward(dy)) AikidokaRenderer.MoveType.OKURI_FORWARD
                else AikidokaRenderer.MoveType.OKURI_BACKWARD
            )
            return
        }

        pendingTapTime = 0L
        onStatus("Not recognised • use circle, circle+back stroke, long body-line drag, top/bottom horizontal stroke, or tap")
    }

    private fun classifyCircle(circle: CircleGesture) {
        val rightLead = aikidokaRenderer.isRightLead()

        // Exact user state table:
        // LEFT lead:  CCW = Irimi; CW = Tenkai; CW+back = Tenkan.
        // RIGHT lead: CW  = Irimi; CCW = Tenkai; CCW+back = Tenkan.
        val irimiDirectionClockwise = rightLead
        val isIrimiDirection = circle.clockwise == irimiDirectionClockwise

        val move = if (isIrimiDirection) {
            AikidokaRenderer.MoveType.IRIMI_TENKAN
        } else if (circle.hasBackTail) {
            AikidokaRenderer.MoveType.TENKAN
        } else {
            AikidokaRenderer.MoveType.TENKAI
        }

        val dir = if (circle.clockwise) "clockwise" else "anticlockwise"
        val lead = if (rightLead) "right leg forward" else "left leg forward"
        val tail = if (circle.hasBackTail) " + back stroke" else ""
        onStatus("$dir$tail • $lead → ${move.label}")
        trigger(move, replaceStatus = false)
    }

    private fun classifyTap(end: PointF) {
        val now = System.currentTimeMillis()
        val close = hypot(
            (end.x - pendingTapX).toDouble(),
            (end.y - pendingTapY).toDouble()
        ) <= 96f * density

        if (pendingTapTime != 0L && now - pendingTapTime in 1L..390L && close) {
            pendingTapTime = 0L
            trigger(AikidokaRenderer.MoveType.TSUGI_FORWARD)
        } else {
            pendingTapTime = now
            pendingTapX = end.x
            pendingTapY = end.y
            onStatus("Tsugi: tap again for forward, or stroke toward the rear for backward")
        }
    }

    /**
     * Finds a full circle, optionally followed by a body-relative backward tail.
     * Prefix testing lets the recognizer isolate the completed circle before the tail.
     */
    private fun detectCircleGesture(points: List<PointF>): CircleGesture? {
        if (points.size < 10) return null
        if (totalPathLength(points) < 145f * density) return null

        val firstCandidate = max(9, points.size / 2)
        var plainCircle: CircleGesture? = null

        for (endIndex in firstCandidate until points.size) {
            val circlePart = points.subList(0, endIndex + 1)
            val stats = circleStats(circlePart)
            if (!stats.valid) continue

            if (endIndex < points.lastIndex - 1) {
                val tail = points.subList(endIndex, points.size)
                val tailLength = totalPathLength(tail)
                val tailDx = tail.last().x - tail.first().x
                val tailDy = tail.last().y - tail.first().y
                val mostlyBodyAxis = abs(tailDy) >= abs(tailDx) * 1.05f
                val backTail = tailLength >= 48f * density &&
                    mostlyBodyAxis &&
                    isScreenStrokeBackward(tailDy)
                if (backTail) return CircleGesture(stats.clockwise, true)
            }

            plainCircle = CircleGesture(stats.clockwise, false)
        }

        return plainCircle
    }

    private fun circleStats(points: List<PointF>): CircleStats {
        if (points.size < 10) return CircleStats(false, false)
        val pathLength = totalPathLength(points)
        if (pathLength < 140f * density) return CircleStats(false, false)

        val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
        var angleTotal = 0.0
        var previous = atan2((points.first().y - cy).toDouble(), (points.first().x - cx).toDouble())

        for (i in 1 until points.size) {
            val current = atan2((points[i].y - cy).toDouble(), (points[i].x - cx).toDouble())
            var delta = current - previous
            while (delta > PI) delta -= 2.0 * PI
            while (delta < -PI) delta += 2.0 * PI
            angleTotal += delta
            previous = current
        }

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val closure = hypot(
            (points.last().x - points.first().x).toDouble(),
            (points.last().y - points.first().y).toDouble()
        ).toFloat()

        val minDiameter = 66f * density
        val closureLimit = max(100f * density, min(width, height) * 0.88f)
        val aspect = if (min(width, height) > 1f) max(width, height) / min(width, height) else 99f
        val valid = abs(angleTotal) >= 1.45 * PI &&
            min(width, height) >= minDiameter &&
            closure <= closureLimit &&
            aspect <= 1.90f

        // Android screen Y increases downward, so positive angle accumulation is clockwise.
        return CircleStats(angleTotal > 0.0, valid)
    }

    private fun isStraightHorizontal(points: List<PointF>): Boolean {
        if (points.size < 2) return false
        val start = points.first()
        val end = points.last()
        val dx = end.x - start.x
        val dy = end.y - start.y
        val chord = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (chord < 1f || abs(dx) < abs(dy) * 2.2f) return false
        return totalPathLength(points) <= chord * 1.24f
    }

    private fun isStraightBodyAxis(points: List<PointF>): Boolean {
        if (points.size < 2) return false
        val start = points.first()
        val end = points.last()
        val dx = end.x - start.x
        val dy = end.y - start.y
        val chord = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (chord < 1f || abs(dy) < abs(dx) * 2.0f) return false
        return totalPathLength(points) <= chord * 1.24f
    }

    private fun isScreenStrokeForward(dy: Float): Boolean {
        // Facing viewer: moving forward comes toward the camera and projects down.
        // Facing away: moving forward projects up.
        return if (aikidokaRenderer.isFacingViewer()) dy > 0f else dy < 0f
    }

    private fun isScreenStrokeBackward(dy: Float): Boolean = !isScreenStrokeForward(dy)

    private fun totalPathLength(points: List<PointF>): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += hypot(
                (points[i].x - points[i - 1].x).toDouble(),
                (points[i].y - points[i - 1].y).toDouble()
            ).toFloat()
        }
        return total
    }

    private fun trigger(move: AikidokaRenderer.MoveType, replaceStatus: Boolean = true) {
        // Capture the starting state before the renderer changes anything.
        val startRightLead = aikidokaRenderer.isRightLead()
        val startFacingViewer = aikidokaRenderer.isFacingViewer()
        if (aikidokaRenderer.trigger(move)) {
            lastStartRightLead = startRightLead
            lastStartFacingViewer = startFacingViewer
            lastStartedMove = move
            if (replaceStatus) {
                val lead = if (startRightLead) "Right" else "Left"
                val facing = if (startFacingViewer) "facing viewer" else "back to viewer"
                onStatus("${move.label} • start: $lead leg forward • $facing")
            }
        } else {
            onStatus("Finish the current movement first")
        }
    }
}
