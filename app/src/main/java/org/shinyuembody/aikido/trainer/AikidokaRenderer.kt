package org.shinyuembody.aikido.trainer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AikidokaRenderer(
    private val onAnimationComplete: (MoveType) -> Unit
) : GLSurfaceView.Renderer {

    enum class MoveType(val label: String) {
        OKURI_FORWARD("Okuri ashi forwards"),
        OKURI_BACKWARD("Okuri ashi backwards"),
        AYUMI_FORWARD("Ayumi ashi forwards"),
        AYUMI_BACKWARD("Ayumi ashi backwards"),
        TSUGI_FORWARD("Tsugi ashi forwards"),
        TSUGI_BACKWARD("Tsugi ashi backwards"),
        TENKAN("Tenkan"),
        IRIMI_TENKAN("Irimi tenkan"),
        TENKAI("Tenkai")
    }

    private data class AnimationRequest(val move: MoveType, val requestedAt: Long)

    private val pending = AtomicReference<AnimationRequest?>(null)
    @Volatile var isAnimating: Boolean = false
        private set

    private lateinit var cube: Mesh
    private lateinit var sphere: Mesh
    private lateinit var hakamaPanel: Mesh

    private var program = 0
    private var aPosition = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uLight = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    private var rootX = 0f
    private var rootZ = 0f
    // Start facing the viewer/camera. In this scene the camera sits on +Z,
    // while the model's local forward direction is -Z at 0 degrees, so 180
    // degrees presents the aikidoka toward the viewer.
    @Volatile private var heading = 180f
    // Default stance is migi hanmi: right foot forward, left foot back.
    @Volatile private var rightLead = true

    // Physical foot positions are stored in world coordinates. Locomotion is
    // driven by these feet rather than by sliding the whole character root.
    // Initial values correspond to migi hanmi while facing the viewer.
    private var leftFootWorldX = 0.22f
    private var leftFootWorldZ = -0.42f
    private var rightFootWorldX = -0.22f
    private var rightFootWorldZ = 0.42f
    private var startLeftFootX = leftFootWorldX
    private var startLeftFootZ = leftFootWorldZ
    private var startRightFootX = rightFootWorldX
    private var startRightFootZ = rightFootWorldZ

    private var activeMove: MoveType? = null
    private var moveStartMs = 0L
    private var moveDurationMs = 800L
    private var startX = 0f
    private var startZ = 0f
    private var startHeading = 0f
    private var targetX = 0f
    private var targetZ = 0f
    private var targetHeading = 0f

    private var leftLegSwing = 0f
    private var rightLegSwing = 0f
    private var leftArmSwing = 0f
    private var rightArmSwing = 0f
    private var torsoTwist = 0f
    private var bodyBob = 0f
    private var hakamaSway = 0f
    private var leftFootLift = 0f
    private var rightFootLift = 0f
    // During compound turns the rendered hanmi can change before the move is
    // committed to the persistent stance state. This lets Tenkai exchange the
    // front/back roles of the feet without a visual snap.
    private var poseRightLeadOverride: Boolean? = null

    private val whiteGi = floatArrayOf(0.94f, 0.95f, 0.92f, 1f)
    private val blackHakama = floatArrayOf(0.035f, 0.045f, 0.04f, 1f)
    private val beltColor = floatArrayOf(0.015f, 0.018f, 0.016f, 1f)
    private val skin = floatArrayOf(0.68f, 0.48f, 0.35f, 1f)
    private val floorColor = floatArrayOf(0.68f, 0.64f, 0.54f, 1f)
    private val lineColor = floatArrayOf(0.34f, 0.31f, 0.25f, 1f)

    fun trigger(move: MoveType): Boolean {
        if (isAnimating || activeMove != null || pending.get() != null) return false
        pending.set(AnimationRequest(move, System.currentTimeMillis()))
        return true
    }

    fun currentHanmiLabel(): String = if (rightLead) "Migi hanmi" else "Hidari hanmi"
    fun currentLeadLegLabel(): String = if (rightLead) "Right leg forward" else "Left leg forward"
    fun currentFacingLabel(): String = if (isFacingViewer()) "facing viewer" else "back to viewer"

    // v3.9: invert Tenkai only. User-verified rule:
    // left leg forward  -> turn RIGHT
    // right leg forward -> turn LEFT
    // The OpenGL/camera convention requires the opposite numeric sign from v3.8.
    private fun tenkaiTurnSign(startRightLead: Boolean): Float = if (startRightLead) 1f else -1f

    // Tenkan needs the opposite visible rotation from the old shared Tenkai sign:
    // left leg forward -> turn RIGHT; right leg forward -> turn LEFT.
    // This is intentionally independent of Tenkai so fixing Tenkan cannot regress Tenkai.
    private fun tenkanTurnSign(startRightLead: Boolean): Float = if (startRightLead) 1f else -1f

    // Irimi-tenkan turn direction is determined ONLY by the lead leg, never by camera/facing:
    // left leg forward  -> anticlockwise
    // right leg forward -> clockwise
    // Matrix.rotateM uses the opposite visual sign in this camera convention, so:
    // right lead = -1, left lead = +1.
    private fun irimiTurnSign(startRightLead: Boolean): Float = if (startRightLead) -1f else 1f

    /** Current physical lead leg. Used by the gesture grammar so a circle can
     * mean Tenkai/Tenkan on the natural side and Irimi-tenkan on the opposite side. */
    fun isRightLead(): Boolean = rightLead

    /**
     * True when the aikidoka's local forward direction points broadly toward
     * the viewer. All turn movements are 180 degrees, so this stays a stable
     * way to make up/down screen gestures relative to the body's facing.
     */
    fun isFacingViewer(): Boolean {
        val h = normalizeHeading(heading)
        return h > 90f && h < 270f
    }

    fun resetPose() {
        pending.set(null)
        activeMove = null
        isAnimating = false
        rootX = 0f
        rootZ = 0f
        heading = 180f
        rightLead = true
        setStandardFeet(rootX, rootZ, heading, rightLead)
        clearBodyMotion()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.055f, 0.11f, 0.09f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        cube = Mesh.cube()
        sphere = Mesh.sphere()
        hakamaPanel = Mesh.frustum()
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uLight = GLES20.glGetUniformLocation(program, "uLightDir")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projection, 0, 42f, ratio, 0.1f, 50f)
        Matrix.setLookAtM(view, 0, 0f, 3.15f, 6.7f, 0f, 1.1f, -0.4f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        startPendingIfNeeded()
        updateAnimation(System.currentTimeMillis())

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniform3f(uLight, -0.35f, 0.85f, 0.45f)

        drawDojo()
        drawAikidoka()
    }

    private fun startPendingIfNeeded() {
        if (activeMove != null) return
        val request = pending.getAndSet(null) ?: return
        val move = request.move
        activeMove = move
        isAnimating = true
        moveStartMs = System.currentTimeMillis()
        moveDurationMs = when (move) {
            MoveType.OKURI_FORWARD, MoveType.OKURI_BACKWARD -> 780L
            MoveType.AYUMI_FORWARD, MoveType.AYUMI_BACKWARD -> 930L
            MoveType.TSUGI_FORWARD, MoveType.TSUGI_BACKWARD -> 920L
            MoveType.TENKAN -> 1460L
            MoveType.IRIMI_TENKAN -> 2200L
            MoveType.TENKAI -> 820L
        }
        startX = rootX
        startZ = rootZ
        startHeading = heading
        startLeftFootX = leftFootWorldX
        startLeftFootZ = leftFootWorldZ
        startRightFootX = rightFootWorldX
        startRightFootZ = rightFootWorldZ
        targetX = rootX
        targetZ = rootZ
        targetHeading = heading

        when (move) {
            MoveType.OKURI_FORWARD -> setTranslationTarget(0.78f)
            MoveType.OKURI_BACKWARD -> setTranslationTarget(-0.72f)
            MoveType.AYUMI_FORWARD -> setTranslationTarget(1.02f)
            MoveType.AYUMI_BACKWARD -> setTranslationTarget(-0.92f)
            MoveType.TSUGI_FORWARD -> setTranslationTarget(0.64f)
            MoveType.TSUGI_BACKWARD -> setTranslationTarget(-0.60f)
            MoveType.TENKAN -> setTenkanTarget()
            MoveType.IRIMI_TENKAN -> setIrimiTenkanTarget()
            MoveType.TENKAI -> {
                // Tenkai is a 180 degree pivot on the spot in the outward/opposite
                // rotational direction. The feet do not take a step and the centre does not translate.
                val turnSign = tenkaiTurnSign(rightLead)
                targetHeading = startHeading + turnSign * 180f
            }
        }
    }

    private fun setTranslationTarget(distance: Float) {
        val rad = startHeading * PI.toFloat() / 180f
        targetX = (startX + sin(rad) * distance).coerceIn(-2.0f, 2.0f)
        targetZ = (startZ - cos(rad) * distance).coerceIn(-3.2f, 1.5f)
    }

    private fun setTenkanTarget() {
        // Tenkan has its own calibrated bilateral turn direction.
        // right lead -> turn LEFT; left lead -> turn RIGHT.
        val turnSign = tenkanTurnSign(rightLead)
        targetHeading = startHeading + turnSign * 180f
        targetX = startX
        targetZ = startZ
    }

    private fun setIrimiTenkanTarget() {
        // Irimi-tenkan changes lead with Ayumi first. The turn direction is
        // determined by that new front leg:
        // new right lead -> turn left, new left lead -> turn right.
        // Irimi direction is the OPPOSITE of Tenkai for the starting lead:
        // start LEFT -> turn LEFT; start RIGHT -> turn RIGHT.
        val turnSign = irimiTurnSign(rightLead)
        targetHeading = startHeading + turnSign * 180f
        targetX = startX
        targetZ = startZ
    }

    private fun updateAnimation(now: Long) {
        val move = activeMove ?: return
        val raw = ((now - moveStartMs).toFloat() / moveDurationMs.toFloat()).coerceIn(0f, 1f)
        val p = smooth(raw)
        clearBodyMotion()

        when (move) {
            MoveType.OKURI_FORWARD -> animateOkuri(p, 1f)
            MoveType.OKURI_BACKWARD -> animateOkuri(p, -1f)
            MoveType.AYUMI_FORWARD -> animateAyumi(p, 1f)
            MoveType.AYUMI_BACKWARD -> animateAyumi(p, -1f)
            MoveType.TSUGI_FORWARD -> animateTsugi(p, 1f)
            MoveType.TSUGI_BACKWARD -> animateTsugi(p, -1f)
            MoveType.TENKAN -> animateTenkan(p)
            MoveType.IRIMI_TENKAN -> animateIrimiTenkan(p)
            MoveType.TENKAI -> animateTenkai(p)
        }

        if (raw >= 1f) {
            // Every animation leaves the physical feet and body centre exactly
            // where its final step placed them. Do not apply a second root
            // translation here, because that creates the redundant glide.
            heading = normalizeHeading(heading)
            // Lead-foot bookkeeping:
            // Ayumi changes lead. Tenkai changes lead because the feet do not step.
            // Tenkan preserves the starting lead because its post-Tenkai back-step
            // restores the original front foot. Irimi-tenkan changes lead once:
            // Ayumi changes side, then Tenkan preserves that newly-created lead.
            if (move == MoveType.AYUMI_FORWARD ||
                move == MoveType.AYUMI_BACKWARD ||
                move == MoveType.TENKAI ||
                move == MoveType.IRIMI_TENKAN) {
                rightLead = !rightLead
            }
            clearBodyMotion()
            activeMove = null
            isAnimating = false
            onAnimationComplete(move)
        }
    }

    private fun animateOkuri(p: Float, direction: Float) {
        // Okuri ashi is two real sliding steps with the same lead side. The body
        // centre is always the midpoint of the feet, so there is no extra root glide.
        heading = startHeading
        poseRightLeadOverride = rightLead
        val f = forwardVector(startHeading)
        val distance = 0.62f * direction
        val frontIsRight = rightLead

        val first = phaseProgress(p, 0f, 0.58f)
        val second = phaseProgress(p, 0.42f, 1f)

        if (direction > 0f) {
            // Front foot advances, rear foot follows.
            if (frontIsRight) {
                setRightFoot(
                    lerp(startRightFootX, startRightFootX + f[0] * distance, first),
                    lerp(startRightFootZ, startRightFootZ + f[1] * distance, first)
                )
                setLeftFoot(
                    lerp(startLeftFootX, startLeftFootX + f[0] * distance, second),
                    lerp(startLeftFootZ, startLeftFootZ + f[1] * distance, second)
                )
                rightFootLift = stepLift(first, 0.035f)
                leftFootLift = stepLift(second, 0.025f)
            } else {
                setLeftFoot(
                    lerp(startLeftFootX, startLeftFootX + f[0] * distance, first),
                    lerp(startLeftFootZ, startLeftFootZ + f[1] * distance, first)
                )
                setRightFoot(
                    lerp(startRightFootX, startRightFootX + f[0] * distance, second),
                    lerp(startRightFootZ, startRightFootZ + f[1] * distance, second)
                )
                leftFootLift = stepLift(first, 0.035f)
                rightFootLift = stepLift(second, 0.025f)
            }
        } else {
            // Rear foot retreats, front foot follows.
            if (frontIsRight) {
                setLeftFoot(
                    lerp(startLeftFootX, startLeftFootX + f[0] * distance, first),
                    lerp(startLeftFootZ, startLeftFootZ + f[1] * distance, first)
                )
                setRightFoot(
                    lerp(startRightFootX, startRightFootX + f[0] * distance, second),
                    lerp(startRightFootZ, startRightFootZ + f[1] * distance, second)
                )
                leftFootLift = stepLift(first, 0.035f)
                rightFootLift = stepLift(second, 0.025f)
            } else {
                setRightFoot(
                    lerp(startRightFootX, startRightFootX + f[0] * distance, first),
                    lerp(startRightFootZ, startRightFootZ + f[1] * distance, first)
                )
                setLeftFoot(
                    lerp(startLeftFootX, startLeftFootX + f[0] * distance, second),
                    lerp(startLeftFootZ, startLeftFootZ + f[1] * distance, second)
                )
                rightFootLift = stepLift(first, 0.035f)
                leftFootLift = stepLift(second, 0.025f)
            }
        }
        updateRootFromFeet()
        val pulse = sin(p * PI.toFloat() * 2f)
        leftArmSwing = -pulse * 5f
        rightArmSwing = pulse * 5f
        bodyBob = (leftFootLift + rightFootLift) * 0.22f
        hakamaSway = pulse * 2.5f
    }

    private fun animateAyumi(p: Float, direction: Float) {
        // Ayumi ashi is a true alternating step. The moving foot stays on its
        // own lateral track and passes the planted foot. This is important:
        // rebuilding a textbook hanmi with leadDelta() after every step adds a
        // small sideways offset and causes compound movements to drift.
        heading = startHeading
        val newRightLead = !rightLead
        val q = smooth(p)
        val stanceGap = 0.84f

        if (direction > 0f) {
            // Old front foot remains planted. Old rear foot passes it and becomes front.
            if (rightLead) {
                val target = footPastPlanted(
                    startLeftFootX, startLeftFootZ,
                    startRightFootX, startRightFootZ,
                    startHeading, stanceGap, true
                )
                setRightFoot(startRightFootX, startRightFootZ)
                setLeftFoot(lerp(startLeftFootX, target[0], q), lerp(startLeftFootZ, target[1], q))
                leftFootLift = stepLift(q, 0.085f)
                leftLegSwing = stepLift(q, 1f) * 12f
            } else {
                val target = footPastPlanted(
                    startRightFootX, startRightFootZ,
                    startLeftFootX, startLeftFootZ,
                    startHeading, stanceGap, true
                )
                setLeftFoot(startLeftFootX, startLeftFootZ)
                setRightFoot(lerp(startRightFootX, target[0], q), lerp(startRightFootZ, target[1], q))
                rightFootLift = stepLift(q, 0.085f)
                rightLegSwing = stepLift(q, 1f) * 12f
            }
        } else {
            // Old rear foot remains planted. Old front foot passes it backwards.
            if (rightLead) {
                val target = footPastPlanted(
                    startRightFootX, startRightFootZ,
                    startLeftFootX, startLeftFootZ,
                    startHeading, stanceGap, false
                )
                setLeftFoot(startLeftFootX, startLeftFootZ)
                setRightFoot(lerp(startRightFootX, target[0], q), lerp(startRightFootZ, target[1], q))
                rightFootLift = stepLift(q, 0.085f)
                rightLegSwing = -stepLift(q, 1f) * 12f
            } else {
                val target = footPastPlanted(
                    startLeftFootX, startLeftFootZ,
                    startRightFootX, startRightFootZ,
                    startHeading, stanceGap, false
                )
                setRightFoot(startRightFootX, startRightFootZ)
                setLeftFoot(lerp(startLeftFootX, target[0], q), lerp(startLeftFootZ, target[1], q))
                leftFootLift = stepLift(q, 0.085f)
                leftLegSwing = -stepLift(q, 1f) * 12f
            }
        }

        poseRightLeadOverride = if (p < 0.58f) rightLead else newRightLead
        updateRootFromFeet()
        val stride = sin(p * PI.toFloat())
        torsoTwist = (if (rightLead) 1f else -1f) * stride * 4f * direction
        leftArmSwing = -rightLegSwing * 0.35f
        rightArmSwing = -leftLegSwing * 0.35f
        bodyBob = (leftFootLift + rightFootLift) * 0.25f
        hakamaSway = stride * 3f * direction
    }

    private fun animateTsugi(p: Float, direction: Float) {
        // Cross-step tsugi ashi. The first foot visibly crosses, then the other
        // foot completes the step. The root is derived from the feet so the
        // former whole-character slide has been removed.
        heading = startHeading
        poseRightLeadOverride = rightLead
        val f = forwardVector(startHeading)
        val r = rightVector(startHeading)
        val shift = 0.62f * direction
        val finalLeftX = startLeftFootX + f[0] * shift
        val finalLeftZ = startLeftFootZ + f[1] * shift
        val finalRightX = startRightFootX + f[0] * shift
        val finalRightZ = startRightFootZ + f[1] * shift

        val q1 = phaseProgress(p, 0f, 0.52f)
        val q2 = phaseProgress(p, 0.48f, 1f)
        val crossSide = if (rightLead) 1f else -1f

        if (direction > 0f) {
            // Rear foot crosses in front; original lead then steps forward.
            if (rightLead) {
                val crossX = startRightFootX + f[0] * 0.16f + r[0] * 0.20f * crossSide
                val crossZ = startRightFootZ + f[1] * 0.16f + r[1] * 0.20f * crossSide
                val rearX = if (q2 <= 0f) lerp(startLeftFootX, crossX, q1) else lerp(crossX, finalLeftX, q2)
                val rearZ = if (q2 <= 0f) lerp(startLeftFootZ, crossZ, q1) else lerp(crossZ, finalLeftZ, q2)
                setLeftFoot(rearX, rearZ)
                setRightFoot(lerp(startRightFootX, finalRightX, q2), lerp(startRightFootZ, finalRightZ, q2))
                leftFootLift = maxOf(stepLift(q1, 0.075f), stepLift(q2, 0.035f))
                rightFootLift = stepLift(q2, 0.075f)
            } else {
                val crossX = startLeftFootX + f[0] * 0.16f + r[0] * 0.20f * crossSide
                val crossZ = startLeftFootZ + f[1] * 0.16f + r[1] * 0.20f * crossSide
                val rearX = if (q2 <= 0f) lerp(startRightFootX, crossX, q1) else lerp(crossX, finalRightX, q2)
                val rearZ = if (q2 <= 0f) lerp(startRightFootZ, crossZ, q1) else lerp(crossZ, finalRightZ, q2)
                setRightFoot(rearX, rearZ)
                setLeftFoot(lerp(startLeftFootX, finalLeftX, q2), lerp(startLeftFootZ, finalLeftZ, q2))
                rightFootLift = maxOf(stepLift(q1, 0.075f), stepLift(q2, 0.035f))
                leftFootLift = stepLift(q2, 0.075f)
            }
        } else {
            // Lead foot crosses behind; original rear foot then retreats.
            if (rightLead) {
                val crossX = startLeftFootX - f[0] * 0.16f - r[0] * 0.20f * crossSide
                val crossZ = startLeftFootZ - f[1] * 0.16f - r[1] * 0.20f * crossSide
                val leadX = if (q2 <= 0f) lerp(startRightFootX, crossX, q1) else lerp(crossX, finalRightX, q2)
                val leadZ = if (q2 <= 0f) lerp(startRightFootZ, crossZ, q1) else lerp(crossZ, finalRightZ, q2)
                setRightFoot(leadX, leadZ)
                setLeftFoot(lerp(startLeftFootX, finalLeftX, q2), lerp(startLeftFootZ, finalLeftZ, q2))
                rightFootLift = maxOf(stepLift(q1, 0.075f), stepLift(q2, 0.035f))
                leftFootLift = stepLift(q2, 0.075f)
            } else {
                val crossX = startRightFootX - f[0] * 0.16f - r[0] * 0.20f * crossSide
                val crossZ = startRightFootZ - f[1] * 0.16f - r[1] * 0.20f * crossSide
                val leadX = if (q2 <= 0f) lerp(startLeftFootX, crossX, q1) else lerp(crossX, finalLeftX, q2)
                val leadZ = if (q2 <= 0f) lerp(startLeftFootZ, crossZ, q1) else lerp(crossZ, finalLeftZ, q2)
                setLeftFoot(leadX, leadZ)
                setRightFoot(lerp(startRightFootX, finalRightX, q2), lerp(startRightFootZ, finalRightZ, q2))
                leftFootLift = maxOf(stepLift(q1, 0.075f), stepLift(q2, 0.035f))
                rightFootLift = stepLift(q2, 0.075f)
            }
        }

        updateRootFromFeet()
        val crossing = sin(p * PI.toFloat())
        torsoTwist = crossSide * crossing * 5f * direction
        bodyBob = (leftFootLift + rightFootLift) * 0.20f
        hakamaSway = crossSide * crossing * 4f
    }

    private fun animateTenkan(p: Float) {
        // TENKAN = TENKAI + STEP BACK.
        //
        // Exact bilateral state rules:
        //   Hidari (left lead)  -> turn RIGHT 180 -> step temporary right-front foot back -> Hidari.
        //   Migi   (right lead) -> turn LEFT  180 -> step temporary left-front foot back  -> Migi.
        //
        // During the 180-degree turn both feet remain on their world-space marks,
        // exactly like Tenkai. This prevents the pivot point from drifting sideways.
        // Only after the turn is complete does the temporary front foot step back
        // on its own lateral track. Therefore Tenkan preserves the starting lead.
        val turnSign = tenkanTurnSign(rightLead)
        val endHeading = startHeading + turnSign * 180f

        val turnQ = phaseProgress(p, 0f, 0.58f)
        val backQ = phaseProgress(p, 0.58f, 1f)

        if (p < 0.58f) {
            // TENKAI phase: fixed feet, fixed ground centre, 180 degree turn.
            heading = startHeading + turnSign * 180f * turnQ
            setLeftFoot(startLeftFootX, startLeftFootZ)
            setRightFoot(startRightFootX, startRightFootZ)
            updateRootFromFeet()
            poseRightLeadOverride = if (turnQ < 0.60f) rightLead else !rightLead

            val pulse = sin(turnQ * PI.toFloat())
            torsoTwist = turnSign * pulse * 4f
            leftArmSwing = turnSign * pulse * 2f
            rightArmSwing = -leftArmSwing
            hakamaSway = turnSign * pulse * 3f
            return
        }

        // STEP-BACK phase. After Tenkai the old rear foot is temporarily in
        // front. The ORIGINAL lead foot stays planted as the fixed pivot. The
        // temporary front foot now steps back into the exact mirrored rear-hanmi
        // position for the ORIGINAL lead side. This is important: moving only
        // along the old foot track leaves the feet laterally crossed after the
        // 180-degree turn even though the state label says the correct lead.
        heading = endHeading
        if (rightLead) {
            // Start Migi: RIGHT is the immutable pivot/lead. LEFT steps back
            // and across to the true rear-left position for migi hanmi.
            val target = standardRearFromLead(
                startRightFootX, startRightFootZ,
                true, endHeading
            )
            setRightFoot(startRightFootX, startRightFootZ)
            setLeftFoot(
                lerp(startLeftFootX, target[0], backQ),
                lerp(startLeftFootZ, target[1], backQ)
            )
            leftFootLift = stepLift(backQ, 0.085f)
            leftLegSwing = -stepLift(backQ, 1f) * 11f
        } else {
            // Start Hidari: LEFT is the immutable pivot/lead. RIGHT steps back
            // and across to the true rear-right position for hidari hanmi.
            val target = standardRearFromLead(
                startLeftFootX, startLeftFootZ,
                false, endHeading
            )
            setLeftFoot(startLeftFootX, startLeftFootZ)
            setRightFoot(
                lerp(startRightFootX, target[0], backQ),
                lerp(startRightFootZ, target[1], backQ)
            )
            rightFootLift = stepLift(backQ, 0.085f)
            rightLegSwing = -stepLift(backQ, 1f) * 11f
        }

        // Immediately after Tenkai the opposite leg is temporarily forward;
        // once the stepping foot passes behind, the original lead is restored.
        poseRightLeadOverride = if (backQ < 0.58f) !rightLead else rightLead
        updateRootFromFeet()
        val stepPulse = sin(backQ * PI.toFloat())
        torsoTwist = turnSign * (1f - backQ) * 2f
        hakamaSway = -turnSign * stepPulse * 3f
        bodyBob = (leftFootLift + rightFootLift) * 0.22f
    }

    private fun animateTenkai(p: Float) {
        // TENKAI: 180 degrees on the spot, with no stepping and no translation.
        // Left lead turns RIGHT; right lead turns LEFT. Because the feet stay in
        // exactly the same world positions while facing reverses, the back foot
        // becomes the front foot and vice versa.
        val turnSign = tenkaiTurnSign(rightLead)
        val q = smooth(p)
        heading = startHeading + turnSign * 180f * q
        setLeftFoot(startLeftFootX, startLeftFootZ)
        setRightFoot(startRightFootX, startRightFootZ)
        updateRootFromFeet()
        poseRightLeadOverride = if (q < 0.60f) rightLead else !rightLead

        val pivot = sin(q * PI.toFloat())
        torsoTwist = turnSign * pivot * 4f
        leftArmSwing = turnSign * pivot * 2f
        rightArmSwing = -leftArmSwing
        bodyBob = 0f
        hakamaSway = turnSign * pivot * 3f
    }

    private fun animateIrimiTenkan(p: Float) {
        // IRIMI-TENKAN = AYUMI FORWARD + TENKAI + STEP BACK.
        //
        // Exact bilateral state table:
        // Start Hidari (LEFT forward):
        //   RIGHT steps forward -> turn ANTICLOCKWISE -> LEFT steps back
        //   -> finish Migi (RIGHT forward).
        //
        // Start Migi (RIGHT forward):
        //   LEFT steps forward -> turn CLOCKWISE -> RIGHT steps back
        //   -> finish Hidari (LEFT forward).
        //
        // All foot travel is along each foot's own track. During the Tenkai
        // phase both feet are fixed. This makes repeated Irimi-tenkan bilateral
        // and removes cumulative left/right drift.
        val ayumiLeadIsRight = !rightLead
        // Explicit bilateral rule from the STARTING lead, independent of facing:
        // start LEFT -> right foot enters -> ANTICLOCKWISE turn -> finish RIGHT lead.
        // start RIGHT -> left foot enters -> CLOCKWISE turn -> finish LEFT lead.
        val turnSign = irimiTurnSign(rightLead)
        val endHeading = startHeading + turnSign * 180f
        val stanceGap = 0.84f

        val ayumiQ = phaseProgress(p, 0f, 0.34f)
        val turnQ = phaseProgress(p, 0.34f, 0.68f)
        val backQ = phaseProgress(p, 0.68f, 1f)

        // Completed Ayumi landing. The original front foot stays planted and
        // the rear foot passes it on its existing lateral track.
        var enterLeftX = startLeftFootX
        var enterLeftZ = startLeftFootZ
        var enterRightX = startRightFootX
        var enterRightZ = startRightFootZ
        if (rightLead) {
            val target = footPastPlanted(
                startLeftFootX, startLeftFootZ,
                startRightFootX, startRightFootZ,
                startHeading, stanceGap, true
            )
            enterLeftX = target[0]
            enterLeftZ = target[1]
        } else {
            val target = footPastPlanted(
                startRightFootX, startRightFootZ,
                startLeftFootX, startLeftFootZ,
                startHeading, stanceGap, true
            )
            enterRightX = target[0]
            enterRightZ = target[1]
        }

        if (p < 0.34f) {
            // AYUMI phase.
            heading = startHeading
            if (rightLead) {
                setRightFoot(startRightFootX, startRightFootZ)
                setLeftFoot(
                    lerp(startLeftFootX, enterLeftX, ayumiQ),
                    lerp(startLeftFootZ, enterLeftZ, ayumiQ)
                )
                leftFootLift = stepLift(ayumiQ, 0.085f)
                leftLegSwing = stepLift(ayumiQ, 1f) * 12f
            } else {
                setLeftFoot(startLeftFootX, startLeftFootZ)
                setRightFoot(
                    lerp(startRightFootX, enterRightX, ayumiQ),
                    lerp(startRightFootZ, enterRightZ, ayumiQ)
                )
                rightFootLift = stepLift(ayumiQ, 0.085f)
                rightLegSwing = stepLift(ayumiQ, 1f) * 12f
            }
            poseRightLeadOverride = if (ayumiQ < 0.60f) rightLead else ayumiLeadIsRight
            updateRootFromFeet()
            bodyBob = (leftFootLift + rightFootLift) * 0.25f
            return
        }

        if (p < 0.68f) {
            // TURN phase. Both feet remain fixed at the Ayumi landing. The visual
            // turn sign comes from the ORIGINAL lead: left start = anticlockwise,
            // right start = clockwise, regardless of whether the body faces the viewer.
            heading = startHeading + turnSign * 180f * turnQ
            setLeftFoot(enterLeftX, enterLeftZ)
            setRightFoot(enterRightX, enterRightZ)
            updateRootFromFeet()
            poseRightLeadOverride = if (turnQ < 0.60f) ayumiLeadIsRight else !ayumiLeadIsRight

            val pulse = sin(turnQ * PI.toFloat())
            torsoTwist = turnSign * pulse * 4f
            leftArmSwing = turnSign * pulse * 2f
            rightArmSwing = -leftArmSwing
            hakamaSway = turnSign * pulse * 3f
            return
        }

        // STEP-BACK phase. The Ayumi foot is the FINAL lead. Previous versions
        // updated the logical lead correctly but left the anatomical feet on the
        // wrong lateral sides after the 180-degree turn. That made a Right -> Left
        // transition still LOOK like right-foot-forward.
        //
        // Build an explicit standard final hanmi for the NEW lead at the final
        // heading. Its centre is constrained to the original attack line, so this
        // fixes the visible lead without reintroducing sideways body drift.
        heading = endHeading

        val provisionalRear = if (rightLead) {
            // Started Migi: LEFT entered, RIGHT is the foot that steps back.
            footPastPlanted(
                enterRightX, enterRightZ,
                enterLeftX, enterLeftZ,
                endHeading, stanceGap, false
            )
        } else {
            // Started Hidari: RIGHT entered, LEFT is the foot that steps back.
            footPastPlanted(
                enterLeftX, enterLeftZ,
                enterRightX, enterRightZ,
                endHeading, stanceGap, false
            )
        }

        val provisionalCenterX = if (rightLead)
            (enterLeftX + provisionalRear[0]) * 0.5f
        else
            (enterRightX + provisionalRear[0]) * 0.5f
        val provisionalCenterZ = if (rightLead)
            (enterLeftZ + provisionalRear[1]) * 0.5f
        else
            (enterRightZ + provisionalRear[1]) * 0.5f

        // Project the final centre back onto the attack line through the start
        // centre. This works for either facing direction, not just screen X = 0.
        val startCenterX = (startLeftFootX + startRightFootX) * 0.5f
        val startCenterZ = (startLeftFootZ + startRightFootZ) * 0.5f
        val attackRight = rightVector(startHeading)
        val lateralError =
            (provisionalCenterX - startCenterX) * attackRight[0] +
            (provisionalCenterZ - startCenterZ) * attackRight[1]
        val finalCenterX = provisionalCenterX - attackRight[0] * lateralError
        val finalCenterZ = provisionalCenterZ - attackRight[1] * lateralError

        // Explicit endpoint: Irimi-tenkan always changes lead.
        val finalRightLead = !rightLead
        val side = if (finalRightLead) 1f else -1f
        val finalLead = rotateYPoint(side * 0.22f, 0f, -0.42f, endHeading)
        val finalRear = rotateYPoint(-side * 0.22f, 0f, 0.42f, endHeading)

        val finalLeftX: Float
        val finalLeftZ: Float
        val finalRightX: Float
        val finalRightZ: Float
        if (finalRightLead) {
            finalRightX = finalCenterX + finalLead[0]
            finalRightZ = finalCenterZ + finalLead[2]
            finalLeftX = finalCenterX + finalRear[0]
            finalLeftZ = finalCenterZ + finalRear[2]
        } else {
            finalLeftX = finalCenterX + finalLead[0]
            finalLeftZ = finalCenterZ + finalLead[2]
            finalRightX = finalCenterX + finalRear[0]
            finalRightZ = finalCenterZ + finalRear[2]
        }

        // Interpolating both endpoints keeps their midpoint on the attack line.
        // Only the original lead foot is lifted, so the motion still reads as
        // the final backward step rather than an extra whole-body translation.
        setLeftFoot(
            lerp(enterLeftX, finalLeftX, backQ),
            lerp(enterLeftZ, finalLeftZ, backQ)
        )
        setRightFoot(
            lerp(enterRightX, finalRightX, backQ),
            lerp(enterRightZ, finalRightZ, backQ)
        )
        if (rightLead) {
            rightFootLift = stepLift(backQ, 0.085f)
            rightLegSwing = -stepLift(backQ, 1f) * 11f
        } else {
            leftFootLift = stepLift(backQ, 0.085f)
            leftLegSwing = -stepLift(backQ, 1f) * 11f
        }

        poseRightLeadOverride = if (backQ < 0.58f) rightLead else finalRightLead
        updateRootFromFeet()
        val stepPulse = sin(backQ * PI.toFloat())
        torsoTwist = turnSign * (1f - backQ) * 2f
        hakamaSway = -turnSign * stepPulse * 3f
        bodyBob = (leftFootLift + rightFootLift) * 0.22f
    }

    /**
     * Rotate a world-space point around an immutable world-space anchor.
     * This is the core of Tenkan: the front foot is the anchor, while the
     * opposite foot and therefore the body centre travel around that point.
     */
    private fun rotateAroundAnchor(
        pointX: Float,
        pointZ: Float,
        anchorX: Float,
        anchorZ: Float,
        degrees: Float
    ): FloatArray {
        val r = degrees * PI.toFloat() / 180f
        val c = cos(r)
        val s = sin(r)
        val dx = pointX - anchorX
        val dz = pointZ - anchorZ
        return floatArrayOf(
            anchorX + dx * c + dz * s,
            anchorZ - dx * s + dz * c
        )
    }

    /**
     * Exact normal hanmi rear-foot position relative to a planted lead foot.
     * This uses the same 0.44 lateral separation and 0.84 front/back separation
     * as setStandardFeet(), so Tenkan finishes in a true mirrored hanmi on both sides.
     */
    private fun standardRearFromLead(
        leadX: Float,
        leadZ: Float,
        rightLead: Boolean,
        degrees: Float
    ): FloatArray {
        val localLeadMinusRearX = if (rightLead) 0.44f else -0.44f
        val localLeadMinusRearZ = -0.84f
        val d = rotateYPoint(localLeadMinusRearX, 0f, localLeadMinusRearZ, degrees)
        return floatArrayOf(leadX - d[0], leadZ - d[2])
    }

    /**
     * Move one foot past a planted foot along the current attack line while
     * preserving the moving foot's lateral track. `toFront=true` places it one
     * stance gap in front of the planted foot; false places it one gap behind.
     */
    private fun footPastPlanted(
        movingX: Float,
        movingZ: Float,
        plantedX: Float,
        plantedZ: Float,
        degrees: Float,
        gap: Float,
        toFront: Boolean
    ): FloatArray {
        val f = forwardVector(degrees)
        val movingLong = movingX * f[0] + movingZ * f[1]
        val plantedLong = plantedX * f[0] + plantedZ * f[1]
        val targetLong = plantedLong + if (toFront) gap else -gap
        val delta = targetLong - movingLong
        return floatArrayOf(movingX + f[0] * delta, movingZ + f[1] * delta)
    }

    private fun setLeftFoot(x: Float, z: Float) {
        leftFootWorldX = x
        leftFootWorldZ = z
    }

    private fun setRightFoot(x: Float, z: Float) {
        rightFootWorldX = x
        rightFootWorldZ = z
    }

    private fun updateRootFromFeet() {
        rootX = ((leftFootWorldX + rightFootWorldX) * 0.5f).coerceIn(-2.0f, 2.0f)
        rootZ = ((leftFootWorldZ + rightFootWorldZ) * 0.5f).coerceIn(-3.2f, 1.5f)
    }

    private fun forwardVector(degrees: Float): FloatArray {
        val r = degrees * PI.toFloat() / 180f
        return floatArrayOf(sin(r), -cos(r))
    }

    private fun rightVector(degrees: Float): FloatArray {
        val r = degrees * PI.toFloat() / 180f
        return floatArrayOf(cos(r), -sin(r))
    }

    /** Vector from the rear foot to the lead foot for a normal hanmi. */
    private fun leadDelta(rightIsLead: Boolean, degrees: Float): FloatArray {
        val localX = if (rightIsLead) 0.28f else -0.28f
        val localZ = -0.60f
        val w = rotateYPoint(localX, 0f, localZ, degrees)
        return floatArrayOf(w[0], w[2])
    }

    private fun leadFromRear(rearX: Float, rearZ: Float, rightIsLead: Boolean, degrees: Float): FloatArray {
        val d = leadDelta(rightIsLead, degrees)
        return floatArrayOf(rearX + d[0], rearZ + d[1])
    }

    private fun rearFromLead(leadX: Float, leadZ: Float, rightIsLead: Boolean, degrees: Float): FloatArray {
        val d = leadDelta(rightIsLead, degrees)
        return floatArrayOf(leadX - d[0], leadZ - d[1])
    }

    private fun setStandardFeet(cx: Float, cz: Float, degrees: Float, rightIsLead: Boolean) {
        val side = if (rightIsLead) 1f else -1f
        val lead = rotateYPoint(side * 0.22f, 0f, -0.42f, degrees)
        val rear = rotateYPoint(-side * 0.22f, 0f, 0.42f, degrees)
        if (rightIsLead) {
            setRightFoot(cx + lead[0], cz + lead[2])
            setLeftFoot(cx + rear[0], cz + rear[2])
        } else {
            setLeftFoot(cx + lead[0], cz + lead[2])
            setRightFoot(cx + rear[0], cz + rear[2])
        }
    }

    private fun worldToRootLocal(wx: Float, wz: Float): FloatArray {
        return rotateYPoint(wx - rootX, 0f, wz - rootZ, -heading)
    }

    private fun phaseProgress(p: Float, from: Float, to: Float): Float {
        if (p <= from) return 0f
        if (p >= to) return 1f
        return smooth(((p - from) / (to - from)).coerceIn(0f, 1f))
    }

    private fun stepLift(progress: Float, height: Float): Float {
        if (progress <= 0f || progress >= 1f) return 0f
        return sin(progress * PI.toFloat()) * height
    }

    private fun drawDojo() {
        drawCube(0f, -0.055f, -1.2f, 5.2f, 0.08f, 6.2f, 0f, 0f, 0f, floorColor)
        for (i in -4..4) {
            drawCube(i * 0.56f, -0.005f, -1.2f, 0.012f, 0.012f, 6.1f, 0f, 0f, 0f, lineColor)
        }
        for (i in -6..4) {
            drawCube(0f, -0.003f, i * 0.48f - 1.2f, 5.0f, 0.012f, 0.012f, 0f, 0f, 0f, lineColor)
        }
    }

    private fun drawAikidoka() {
        val root = FloatArray(16)
        Matrix.setIdentityM(root, 0)
        Matrix.translateM(root, 0, rootX, bodyBob, rootZ)
        Matrix.rotateM(root, 0, heading, 0f, 1f, 0f)

        // Aikido hanmi is deliberately asymmetrical. The lead foot and hand point
        // toward the line of attack, the rear foot opens outward, and the pelvis,
        // shoulders and hands form one connected triangular posture.
        val poseRightLead = poseRightLeadOverride ?: rightLead
        val side = if (poseRightLead) 1f else -1f
        val hipYaw = side * 32f + torsoTwist * 0.55f
        val shoulderYaw = side * 24f + torsoTwist
        val headYaw = side * 4f + torsoTwist * 0.18f

        // Feet are physical world-space points. Convert them into the current
        // body frame for drawing. This prevents any foot snap when Tenkai changes
        // the facing direction and swaps front/back roles.
        val leftLocal = worldToRootLocal(leftFootWorldX, leftFootWorldZ)
        val rightLocal = worldToRootLocal(rightFootWorldX, rightFootWorldZ)
        val leftLegX = leftLocal[0]
        val leftLegZ = leftLocal[2]
        val rightLegX = rightLocal[0]
        val rightLegZ = rightLocal[2]

        val leftFootYaw = if (poseRightLead) 36f else 0f
        val rightFootYaw = if (poseRightLead) 0f else -36f

        // Slightly bent, staggered legs. A little more weight is shown over the
        // front leg without making the posture look like a lunge.
        drawPart(cube, root, leftLegX, 0.31f + leftFootLift * 0.45f, leftLegZ, 0.17f, 0.58f, 0.18f,
            leftLegSwing, 0f, 0f, blackHakama)
        drawPart(cube, root, rightLegX, 0.31f + rightFootLift * 0.45f, rightLegZ, 0.17f, 0.58f, 0.18f,
            rightLegSwing, 0f, 0f, blackHakama)

        // Feet: front foot on the attack line, rear foot opened roughly 30 degrees.
        val leftFootToeOffset = if (poseRightLead) -0.04f else -0.15f
        val rightFootToeOffset = if (poseRightLead) -0.15f else -0.04f
        drawPart(cube, root, leftLegX, 0.07f + leftFootLift, leftLegZ + leftFootToeOffset, 0.23f, 0.10f, 0.46f,
            0f, leftFootYaw, 0f, beltColor)
        drawPart(cube, root, rightLegX, 0.07f + rightFootLift, rightLegZ + rightFootToeOffset, 0.23f, 0.10f, 0.46f,
            0f, rightFootYaw, 0f, beltColor)

        // Hakama and obi follow the pelvis rather than sitting square to the camera.
        drawPart(hakamaPanel, root, -0.21f, 0.79f, 0.01f, 1.0f, 1.03f, 1.0f,
            hakamaSway, hipYaw, -3f, blackHakama)
        drawPart(hakamaPanel, root, 0.21f, 0.79f, 0.01f, 1.0f, 1.03f, 1.0f,
            -hakamaSway, hipYaw, 3f, blackHakama)
        drawPart(cube, root, 0f, 1.27f, 0f, 0.88f, 0.12f, 0.34f,
            0f, hipYaw, 0f, beltColor)

        // Gi torso and lapels follow the shoulder line. The pelvis is slightly more
        // turned than the shoulders, keeping the chest relaxed rather than twisted.
        drawPart(cube, root, 0f, 1.66f, 0f, 0.88f, 0.68f, 0.42f,
            0f, shoulderYaw, 0f, whiteGi)
        drawPart(cube, root, -0.11f, 1.73f, 0.225f, 0.10f, 0.60f, 0.035f,
            0f, shoulderYaw, -24f, floatArrayOf(0.78f, 0.80f, 0.76f, 1f))
        drawPart(cube, root, 0.11f, 1.73f, 0.235f, 0.10f, 0.60f, 0.035f,
            0f, shoulderYaw, 24f, floatArrayOf(0.82f, 0.84f, 0.80f, 1f))

        drawHanmiArms(root, shoulderYaw, poseRightLead)

        // Head remains mostly on the line of attack instead of being locked to
        // the torso angle.
        drawPart(cube, root, 0f, 2.06f, 0f, 0.16f, 0.18f, 0.16f,
            0f, headYaw, 0f, skin)
        drawPart(sphere, root, 0f, 2.31f, 0f, 0.25f, 0.29f, 0.25f,
            0f, headYaw, 0f, skin)
        drawPart(sphere, root, 0f, 2.43f, -0.015f, 0.255f, 0.13f, 0.255f,
            0f, headYaw, 0f, blackHakama)
    }

    private fun drawHanmiArms(root: FloatArray, shoulderYaw: Float, poseRightLead: Boolean) {
        val side = if (poseRightLead) 1f else -1f
        val leftIsLead = !poseRightLead

        val leftShoulder = rotateYPoint(-0.43f, 1.82f, 0f, shoulderYaw)
        val rightShoulder = rotateYPoint(0.43f, 1.82f, 0f, shoulderYaw)

        // Kamae: lead hand is farther forward and near centre-line height;
        // rear hand protects the centre near the lower ribs/tanden.
        fun armPoints(isLeft: Boolean, isLead: Boolean, swing: Float): Pair<FloatArray, FloatArray> {
            val xSign = if (isLeft) -1f else 1f
            val swingRad = swing * PI.toFloat() / 180f
            val dynamicForward = -sin(swingRad) * 0.20f
            val dynamicLift = kotlin.math.abs(sin(swingRad)) * 0.045f

            val elbow = if (isLead) {
                // Lead elbow points down and forward, keeping the arm connected
                // to the centre rather than flaring sideways.
                floatArrayOf(xSign * 0.30f, 1.52f + dynamicLift, -0.43f + dynamicForward * 0.45f)
            } else {
                // Rear elbow stays close to the ribs.
                floatArrayOf(xSign * 0.36f, 1.45f + dynamicLift, -0.02f + dynamicForward * 0.25f)
            }
            val hand = if (isLead) {
                // The lead hand is unmistakably in front on the centre line.
                floatArrayOf(xSign * 0.10f, 1.39f + dynamicLift, -0.94f + dynamicForward)
            } else {
                // Rear hand remains near the tanden/hip area.
                floatArrayOf(xSign * 0.24f, 1.23f + dynamicLift, -0.12f + dynamicForward * 0.45f)
            }
            return Pair(rotateYPoint(elbow[0], elbow[1], elbow[2], shoulderYaw),
                rotateYPoint(hand[0], hand[1], hand[2], shoulderYaw))
        }

        val (leftElbow, leftHand) = armPoints(true, leftIsLead, leftArmSwing)
        val (rightElbow, rightHand) = armPoints(false, !leftIsLead, rightArmSwing)

        // Upper sleeves are broad, forearms narrower, producing bent relaxed elbows.
        drawSegment(root, leftShoulder, leftElbow, 0.22f, whiteGi)
        drawSegment(root, rightShoulder, rightElbow, 0.22f, whiteGi)
        drawSegment(root, leftElbow, leftHand, 0.16f, whiteGi)
        drawSegment(root, rightElbow, rightHand, 0.16f, whiteGi)

        drawPart(sphere, root, leftHand[0], leftHand[1], leftHand[2],
            0.115f, 0.105f, 0.14f, 0f, shoulderYaw, 0f, skin)
        drawPart(sphere, root, rightHand[0], rightHand[1], rightHand[2],
            0.115f, 0.105f, 0.14f, 0f, shoulderYaw, 0f, skin)
    }

    private fun rotateYPoint(x: Float, y: Float, z: Float, degrees: Float): FloatArray {
        val r = degrees * PI.toFloat() / 180f
        val c = cos(r)
        val s = sin(r)
        return floatArrayOf(x * c + z * s, y, -x * s + z * c)
    }

    private fun drawSegment(
        root: FloatArray,
        start: FloatArray,
        end: FloatArray,
        thickness: Float,
        color: FloatArray
    ) {
        val dx = end[0] - start[0]
        val dy = end[1] - start[1]
        val dz = end[2] - start[2]
        val length = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)
        val nx = dx / length
        val ny = dy / length
        val nz = dz / length
        val angle = acos(ny.coerceIn(-1f, 1f)) * 180f / PI.toFloat()
        val axisX = nz
        val axisZ = -nx
        val axisLength = sqrt(axisX * axisX + axisZ * axisZ)

        System.arraycopy(root, 0, model, 0, 16)
        Matrix.translateM(model, 0,
            (start[0] + end[0]) * 0.5f,
            (start[1] + end[1]) * 0.5f,
            (start[2] + end[2]) * 0.5f)
        if (axisLength > 0.0001f) {
            Matrix.rotateM(model, 0, angle, axisX / axisLength, 0f, axisZ / axisLength)
        } else if (ny < 0f) {
            Matrix.rotateM(model, 0, 180f, 1f, 0f, 0f)
        }
        Matrix.scaleM(model, 0, thickness, length, thickness)
        drawMesh(cube, model, color)
    }

    private fun drawCube(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rx: Float, ry: Float, rz: Float,
        color: FloatArray
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.rotateM(model, 0, ry, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, rx, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        drawMesh(cube, model, color)
    }

    private fun drawPart(
        mesh: Mesh,
        root: FloatArray,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rx: Float, ry: Float, rz: Float,
        color: FloatArray
    ) {
        System.arraycopy(root, 0, model, 0, 16)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.rotateM(model, 0, ry, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, rx, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        drawMesh(mesh, model, color)
    }

    private fun drawMesh(mesh: Mesh, modelMatrix: FloatArray, color: FloatArray) {
        Matrix.multiplyMM(mvp, 0, vp, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
        GLES20.glUniform4fv(uColor, 1, color, 0)
        mesh.draw(aPosition, aNormal)
    }

    private fun clearBodyMotion() {
        leftLegSwing = 0f
        rightLegSwing = 0f
        leftArmSwing = 0f
        rightArmSwing = 0f
        torsoTwist = 0f
        bodyBob = 0f
        hakamaSway = 0f
        leftFootLift = 0f
        rightFootLift = 0f
        poseRightLeadOverride = null
    }

    private fun phaseSwing(p: Float, from: Float, to: Float): Float {
        if (p <= from || p >= to) return 0f
        val q = ((p - from) / (to - from)).coerceIn(0f, 1f)
        return sin(q * PI.toFloat())
    }

    private fun smooth(v: Float): Float = v * v * (3f - 2f * v)
    private fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p
    private fun normalizeHeading(v: Float): Float {
        var h = v % 360f
        if (h > 180f) h -= 360f
        if (h < -180f) h += 360f
        return h
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
        return GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, vs)
            GLES20.glAttachShader(p, fs)
            GLES20.glLinkProgram(p)
            val status = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(p)
                GLES20.glDeleteProgram(p)
                error("OpenGL program link failed: $log")
            }
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                error("OpenGL shader compile failed: $log")
            }
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
                vNormal = normalize(mat3(uModel) * aNormal);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLightDir;
            varying vec3 vNormal;
            void main() {
                float diffuse = max(dot(normalize(vNormal), normalize(uLightDir)), 0.0);
                float light = 0.40 + 0.60 * diffuse;
                gl_FragColor = vec4(uColor.rgb * light, uColor.a);
            }
        """
    }
}
