package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Firework burst shapes (v10.8 + v10.9). */
enum class FwShape { PEONY, CHRYSANTHEMUM, RING, DOUBLE_RING, WILLOW, PALM, HEART, STAR, GLITTER, MULTI_STAGE, SPIRAL,
    TRIPLE_RING, DAHLIA, KAMURO, HORSETAIL, CROSSETTE, COMET, CRACKLE, STROBE, SMILEY }

/** Festive palettes: [a] start colour, [b] the colour sparks shift into over their life; RAINBOW cycles per spark. */
enum class FwPalette(val a: Int, val b: Int) {
    GOLD(Color.rgb(0xFF, 0xE0, 0x7A), Color.rgb(0xFF, 0x9A, 0x1E)),
    RED(Color.rgb(0xFF, 0x6A, 0x5A), Color.rgb(0xD0, 0x10, 0x30)),
    GREEN(Color.rgb(0xB6, 0xFF, 0x8A), Color.rgb(0x18, 0xC0, 0x4A)),
    BLUE(Color.rgb(0x9A, 0xF0, 0xFF), Color.rgb(0x1A, 0x9C, 0xFF)),
    PURPLE(Color.rgb(0xF0, 0xA8, 0xFF), Color.rgb(0xB0, 0x2B, 0xE2)),
    SILVER(Color.rgb(0xFF, 0xFF, 0xFF), Color.rgb(0xB8, 0xC4, 0xD8)),
    RAINBOW(Color.WHITE, Color.WHITE),
    RED_GOLD(Color.rgb(0xFF, 0x40, 0x40), Color.rgb(0xFF, 0xD0, 0x40)),
    BLUE_SILVER(Color.rgb(0x40, 0xE0, 0xFF), Color.rgb(0xF0, 0xF4, 0xFF)),
    GREEN_GOLD(Color.rgb(0x3A, 0xE0, 0x6A), Color.rgb(0xFF, 0xD8, 0x50)),
    PINK_PURPLE(Color.rgb(0xFF, 0x6A, 0xD8), Color.rgb(0x9A, 0x4A, 0xFF)),
}

/** One burst's randomised recipe (all numbers in px at the given scale). */
class FwSpec(
    val shape: FwShape, val palette: FwPalette,
    val x: Float, val y: Float,
    val count: Int, val speed: Float, val gravity: Float, val drag: Float,
    val life: Float, val trail: Float, val twinkle: Float, val size: Float,
    /** Sound: boom pitch 0.75..1.35 and whether a crackle tail follows. */
    val boomPitch: Float, val crackle: Boolean,
    val delay: Float,
    /** v10.9: the recipe it was rolled from, and how many concentric layers it bursts into. */
    val recipe: FwRecipe = FwRecipe(shape.name, shape, 1),
    val rings: Int = recipe.rings,
    /** v10.9: the palettes of the extra layers (main palette first). */
    val ringPalettes: List<FwPalette> = listOf(palette),
)

/**
 * Procedural fireworks (Skye's sky show, Smash rocket hits). v10.9: every burst comes from a named recipe in
 * [FireworkRecipes] (50+), layered by default: 2-4 concentric rings/spheres at different radii and colours, a crackle /
 * glitter / strobe core, willow / crossette / comet / horsetail outer layers, colour change and multi-break stars.
 * Never the same recipe, shape or palette twice in a row. Sparks are drawn as thin tapered streaks bright at the tip,
 * with twinkle stars around each burst. Shells fly a real wobbly rocket path with a dotted glitter trail.
 * Everything random comes from its OWN seeded [Random] (never the frame-loop RNG). A fixed pool ([cap] live sparks,
 * default 600) means nothing is allocated per frame; when a new burst needs room the OLDEST sparks are faded out
 * (evicted) first, so the cap always holds and new bursts never look empty.
 */
class Fireworks(seed: Long = 1L, val cap: Int = CAP) {
    private val rnd = Random(seed)
    // spark pool
    private val x = FloatArray(cap); private val y = FloatArray(cap)
    private val vx = FloatArray(cap); private val vy = FloatArray(cap)
    private val age = FloatArray(cap); private val life = FloatArray(cap)
    private val c0 = IntArray(cap); private val c1 = IntArray(cap)
    private val sz = FloatArray(cap); private val tr = FloatArray(cap); private val tw = FloatArray(cap)
    private val grav = FloatArray(cap); private val drg = FloatArray(cap); private val spin = FloatArray(cap)
    private val kind = IntArray(cap)
    var count = 0; private set
    /** Highest live count ever seen (tests). */
    var peak = 0; private set
    /** Sparks evicted early to make room (tests / degrade gracefully). */
    var evicted = 0; private set

    // rising shells (v10.9: real rocket physics with a wobble)
    private val maxQ = 16
    private val qSpec = arrayOfNulls<FwSpec>(maxQ); private val qT = FloatArray(maxQ) { -1f }
    private val qX = FloatArray(maxQ); private val qY = FloatArray(maxQ)
    private val qVX = FloatArray(maxQ); private val qVY = FloatArray(maxQ)
    private val qWob = FloatArray(maxQ); private val qFlight = FloatArray(maxQ); private val qS = FloatArray(maxQ); private val qDelay = FloatArray(maxQ)

    private var lastShape: FwShape? = null
    private var lastPalette: FwPalette? = null
    private var lastRecipe: FwRecipe? = null
    /** Bursts so far (tests / variety). */
    val history = ArrayList<FwSpec>()

    /** Sound hook: (pitch, crackle, big) at the moment a burst pops. */
    var onBoom: ((Float, Boolean, Boolean) -> Unit)? = null
    /** Whistle hook when a shell launches. */
    var onLaunch: (() -> Unit)? = null

    val active get() = count > 0 || qT.any { it >= 0f }
    /** Shells still rising. */
    val rising get() = qT.count { it >= 0f }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    fun clear() { count = 0; qT.fill(-1f); qSpec.fill(null) }

    /**
     * Roll a new recipe at (bx, by). [s] = px per dp. Never repeats the previous recipe, shape or palette; [avoid] =
     * recipes already used in this show (one power = 5 different recipes).
     */
    fun nextSpec(bx: Float, by: Float, s: Float, delay: Float = 0f, big: Boolean = false, avoid: Collection<FwRecipe> = emptyList()): FwSpec {
        var rec: FwRecipe
        var guard = 0
        do { rec = FireworkRecipes.ALL[rnd.nextInt(FireworkRecipes.ALL.size)]; guard++ }
        while ((rec == lastRecipe || rec.shape == lastShape || (rec in avoid && guard < 200) || (rec.palette != null && rec.palette == lastPalette)) && guard < 400)
        val shape = rec.shape
        var pal: FwPalette
        if (rec.palette != null) pal = rec.palette
        else {
            do { pal = FwPalette.values()[rnd.nextInt(FwPalette.values().size)] } while (pal == lastPalette)
            if (shape == FwShape.WILLOW && rnd.nextFloat() < 0.6f && lastPalette != FwPalette.GOLD) pal = FwPalette.GOLD
        }
        lastShape = shape; lastPalette = pal; lastRecipe = rec
        val k = if (big) 1.3f else 1f
        val sizeK = (0.75f + rnd.nextFloat() * 0.55f) * k
        val base = when (shape) {
            FwShape.WILLOW, FwShape.KAMURO -> 130f; FwShape.PALM, FwShape.HORSETAIL -> 220f; FwShape.GLITTER -> 170f; FwShape.SPIRAL -> 150f
            FwShape.COMET -> 180f; FwShape.DAHLIA -> 190f; else -> 210f
        }
        val count = when (shape) {
            FwShape.PALM -> 40; FwShape.RING -> 36; FwShape.DOUBLE_RING -> 52; FwShape.TRIPLE_RING -> 60; FwShape.HEART, FwShape.STAR, FwShape.SMILEY -> 50
            FwShape.GLITTER -> 64; FwShape.MULTI_STAGE -> 22; FwShape.WILLOW, FwShape.KAMURO -> 44; FwShape.SPIRAL -> 40; FwShape.COMET -> 14
            FwShape.HORSETAIL -> 30; FwShape.CROSSETTE -> 12; FwShape.DAHLIA -> 30; else -> 56
        }.let { (it * (0.8f + rnd.nextFloat() * 0.35f)).toInt().coerceIn(8, MAX_PER_BURST) }
        val gravity = when (shape) { FwShape.WILLOW, FwShape.KAMURO, FwShape.HORSETAIL -> 120f; FwShape.PALM -> 90f; else -> 45f + rnd.nextFloat() * 45f } * s
        val drag = when (shape) { FwShape.WILLOW, FwShape.KAMURO -> 0.9f; FwShape.CHRYSANTHEMUM -> 1.6f; FwShape.COMET -> 0.8f; else -> 1.2f + rnd.nextFloat() * 1.0f }
        val life = when (shape) { FwShape.WILLOW, FwShape.KAMURO, FwShape.HORSETAIL -> 2.1f; FwShape.GLITTER -> 1.5f; FwShape.COMET -> 1.7f; else -> 1.1f + rnd.nextFloat() * 0.6f }
        // v10.9: streaks, not dots (the reference look): every star leaves a short tapered trail
        val trail = when (shape) { FwShape.CHRYSANTHEMUM, FwShape.WILLOW, FwShape.KAMURO, FwShape.HORSETAIL, FwShape.COMET -> 0.12f; FwShape.PALM -> 0.09f; else -> 0.05f + rnd.nextFloat() * 0.04f }
        val twinkle = when (shape) { FwShape.GLITTER, FwShape.STROBE -> 1f; FwShape.WILLOW, FwShape.KAMURO -> 0.25f; else -> rnd.nextFloat() * 0.5f }
        val pitch = (1.35f - 0.5f * ((sizeK - 0.75f) / 0.85f).coerceIn(0f, 1f)) * (0.92f + rnd.nextFloat() * 0.16f)
        val pals = ArrayList<FwPalette>(rec.rings); pals += pal
        while (pals.size < rec.rings) {
            val p = FwPalette.values()[rnd.nextInt(FwPalette.values().size)]
            if (p !in pals && p != FwPalette.RAINBOW) pals += p
        }
        return FwSpec(shape, pal, bx, by, count, base * sizeK * s, gravity, drag, life, trail, twinkle,
            (1.5f + rnd.nextFloat() * 1.0f) * s, pitch.coerceIn(0.7f, 1.4f),
            rec.core == FwCore.CRACKLE || shape == FwShape.GLITTER || shape == FwShape.MULTI_STAGE || rnd.nextFloat() < 0.25f, delay, rec, rec.rings, pals)
    }

    /**
     * Fire one firework from (fromX, fromY) that rises and bursts near (toX, toY). ~5% = grand finale: 3 staggered
     * bursts around the target. Returns how many bursts were scheduled.
     */
    fun launch(fromX: Float, fromY: Float, toX: Float, toY: Float, s: Float, finaleChance: Float = 0.05f): Int {
        val finale = rnd.nextFloat() < finaleChance
        val n = if (finale) 3 else 1
        for (i in 0 until n) {
            val ox = if (i == 0) 0f else (if (i == 1) -1f else 1f) * (50f + rnd.nextFloat() * 40f) * s
            val oy = if (i == 0) 0f else -(20f + rnd.nextFloat() * 50f) * s
            shell(nextSpec(toX + ox, toY + oy, s, big = finale && i == n - 1), fromX, fromY, s, i * 0.32f)
        }
        onLaunch?.invoke()
        return n
    }

    /** v10.9: one shell (a little rocket) from (fromX, fromY) that wobbles up and bursts at its spec's point. */
    fun launchShell(sp: FwSpec, fromX: Float, fromY: Float, s: Float): Boolean { val ok = shell(sp, fromX, fromY, s, 0f); if (ok) onLaunch?.invoke(); return ok }

    /** Burst right now at (bx, by) with a fresh recipe (Smash impacts). */
    fun burstAt(bx: Float, by: Float, s: Float, big: Boolean = false): FwSpec { val sp = nextSpec(bx, by, s, big = big); burst(sp); return sp }

    private fun shell(sp: FwSpec, fx: Float, fy: Float, s: Float, wait: Float): Boolean {
        val i = (0 until maxQ).firstOrNull { qT[it] < 0f } ?: return false
        val flight = RISE + 0.35f + rnd.nextFloat() * 0.35f
        qSpec[i] = sp; qT[i] = 0f; qDelay[i] = wait; qX[i] = fx; qY[i] = fy; qFlight[i] = flight
        // initial velocity so it arrives near the burst point (the slow-down and the wobble bend the path)
        qVX[i] = (sp.x - fx) / flight; qVY[i] = (sp.y - fy) / flight
        qWob[i] = rnd.nextFloat() * 6.283f; qS[i] = s
        return true
    }

    private fun push(px: Float, py: Float, pvx: Float, pvy: Float, l: Float, a: Int, b: Int, size: Float, trail: Float, twinkle: Float,
                     g: Float, d: Float, sp: Float, k: Int): Boolean {
        if (count >= cap) return false
        val i = count++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; age[i] = 0f; life[i] = l; c0[i] = a; c1[i] = b
        sz[i] = size; tr[i] = trail; tw[i] = twinkle; grav[i] = g; drg[i] = d; spin[i] = sp; kind[i] = k
        if (count > peak) peak = count
        return true
    }

    /** Degrade gracefully: make room for [n] new sparks by evicting the oldest (most faded) ones. */
    private fun makeRoom(n: Int) {
        var need = count + n - cap
        while (need > 0 && count > 0) {
            var best = 0; var bestF = -1f
            for (i in 0 until count) { val f = age[i] / life[i]; if (f > bestF) { bestF = f; best = i } }
            remove(best); evicted++; need--
        }
    }

    private fun colours(pal: FwPalette, i: Int, n: Int, out: IntArray) {
        if (pal == FwPalette.RAINBOW) {
            val h = (i.toFloat() / n.coerceAtLeast(1)) * 6f
            out[0] = hue(h); out[1] = hue(h + 1.2f)
        } else if (pal == FwPalette.RED_GOLD || pal == FwPalette.BLUE_SILVER || pal == FwPalette.GREEN_GOLD || pal == FwPalette.PINK_PURPLE) {
            if (i % 2 == 0) { out[0] = pal.a; out[1] = pal.b } else { out[0] = pal.b; out[1] = pal.a }
        } else { out[0] = pal.a; out[1] = pal.b }
    }
    private val cc = IntArray(2)

    /** How many sparks [sp] will need (for eviction). */
    private fun budget(sp: FwSpec): Int {
        val r = sp.recipe
        return sp.count + (sp.rings - 1) * RING_COUNT + (if (r.core != FwCore.NONE) 16 else 0) +
            (when (r.outer) { FwOuter.NONE -> 0; FwOuter.WILLOW -> 18; FwOuter.CROSSETTE -> 7; FwOuter.COMET -> 6; FwOuter.HORSETAIL -> 16 }) + TWINKLES + 1
    }

    /** Spawn the sparks of [sp]: the main shape, the extra rings, the core, the outer layer, twinkles and a flash. */
    fun burst(sp: FwSpec) {
        history += sp; if (history.size > 64) history.removeAt(0)
        onBoom?.invoke(sp.boomPitch, sp.crackle, sp.speed > 260f)
        makeRoom(budget(sp))
        val n = sp.count
        val rec = sp.recipe
        val tilt = 0.55f + rnd.nextFloat() * 0.45f        // rings are seen at an angle
        val rot = rnd.nextFloat() * 2f * PI.toFloat()
        val spinDir = if (rnd.nextBoolean()) 1f else -1f
        val changeTo = if (rec.colourChange) sp.ringPalettes.getOrNull(1)?.a ?: FwPalette.SILVER.a else 0
        when (sp.shape) {
            FwShape.PALM, FwShape.HORSETAIL -> {
                val arms = if (sp.shape == FwShape.PALM) 5 + rnd.nextInt(4) else 8 + rnd.nextInt(4)
                val per = (n / arms).coerceAtLeast(3)
                for (a in 0 until arms) {
                    val ang = if (sp.shape == FwShape.HORSETAIL) -PI.toFloat() / 2f + (a - arms / 2f) * 0.22f else rot + a * 2f * PI.toFloat() / arms
                    colours(sp.palette, a, arms, cc)
                    for (j in 0 until per) {
                        val f = 0.45f + 0.55f * j / (per - 1).coerceAtLeast(1)
                        val v = sp.speed * f * (if (sp.shape == FwShape.HORSETAIL) 0.8f else 1f)
                        push(sp.x, sp.y, cos(ang) * v, sin(ang) * v - 30f, sp.life * (0.8f + 0.3f * f), cc[0], cc[1], sp.size * (1.5f - 0.5f * f), sp.trail, sp.twinkle, sp.gravity, sp.drag, 0f, K_SPARK)
                    }
                }
            }
            else -> for (i in 0 until n) {
                val u = i.toFloat() / n
                val th = rot + u * 2f * PI.toFloat()
                var dx = cos(th); var dy = sin(th); var v = sp.speed
                var k = K_SPARK; var sp2 = 0f
                when (sp.shape) {
                    FwShape.PEONY, FwShape.DAHLIA -> v *= 0.72f + 0.28f * rnd.nextFloat()
                    FwShape.CHRYSANTHEMUM -> v *= 0.55f + 0.45f * rnd.nextFloat()
                    FwShape.RING -> dy *= tilt
                    FwShape.DOUBLE_RING -> { dy *= tilt; if (i % 2 == 1) v *= 0.58f }
                    FwShape.TRIPLE_RING -> { dy *= tilt; v *= when (i % 3) { 0 -> 1f; 1 -> 0.7f; else -> 0.42f } }
                    FwShape.WILLOW, FwShape.KAMURO -> v *= 0.6f + 0.4f * rnd.nextFloat()
                    FwShape.HEART -> {
                        val t = u * 2f * PI.toFloat()
                        val hx = 16f * sin(t).pow(3); val hy = -(13f * cos(t) - 5f * cos(2 * t) - 2f * cos(3 * t) - cos(4 * t))
                        dx = hx / 17f; dy = hy / 17f
                    }
                    FwShape.STAR -> {
                        val local = (u * 5f) % 1f
                        val r = 0.45f + 0.55f * abs(1f - 2f * local)
                        dx *= r; dy *= r
                    }
                    FwShape.SMILEY -> {
                        // a face: ring + two eyes + a smile
                        when {
                            i < n * 0.55f -> Unit
                            i < n * 0.65f -> { dx = -0.35f + 0.04f * (i % 3); dy = -0.3f }
                            i < n * 0.75f -> { dx = 0.35f - 0.04f * (i % 3); dy = -0.3f }
                            else -> { val a = PI.toFloat() * (0.2f + 0.6f * (u - 0.75f) / 0.25f); dx = cos(a) * 0.55f; dy = sin(a) * 0.55f }
                        }
                    }
                    FwShape.GLITTER -> { v *= 0.3f + 0.7f * sqrt(rnd.nextFloat()); k = K_GLITTER }
                    FwShape.MULTI_STAGE -> { v *= 0.85f + 0.15f * rnd.nextFloat(); k = K_STAGE }
                    FwShape.SPIRAL -> { v *= 0.8f; sp2 = spinDir * (2.6f + rnd.nextFloat()) }
                    FwShape.CROSSETTE -> { v *= 0.8f; k = K_CROSS }
                    FwShape.COMET -> v *= 0.7f + 0.3f * rnd.nextFloat()
                    FwShape.CRACKLE -> { v *= 0.5f + 0.5f * rnd.nextFloat(); k = K_CRACKLE }
                    FwShape.STROBE -> { v *= 0.6f + 0.4f * rnd.nextFloat(); k = K_STROBE }
                    FwShape.PALM, FwShape.HORSETAIL -> Unit
                }
                if (rec.multiBreak && k == K_SPARK && i % 4 == 0) k = K_STAGE
                colours(sp.palette, i, n, cc)
                val big = if (sp.shape == FwShape.COMET) 2.2f else 1f
                push(sp.x, sp.y, dx * v, dy * v, sp.life * (0.85f + 0.3f * rnd.nextFloat()), cc[0], if (rec.colourChange) changeTo else cc[1],
                    sp.size * (0.8f + 0.4f * rnd.nextFloat()) * big, sp.trail * (if (sp.shape == FwShape.COMET) 2f else 1f), sp.twinkle, sp.gravity, sp.drag, sp2, k)
            }
        }
        // v10.9 layered: extra concentric rings / spheres at other radii and colours
        val radii = floatArrayOf(0.5f, 1.3f, 0.75f)
        for (L in 1 until sp.rings) {
            val pal = sp.ringPalettes.getOrElse(L) { FwPalette.SILVER }
            val flat = L % 2 == 1
            val rt = rnd.nextFloat() * 6.283f
            val vk = radii[(L - 1) % radii.size]
            for (j in 0 until RING_COUNT) {
                val th = rt + j * 6.283f / RING_COUNT
                val v = sp.speed * vk * (0.92f + 0.12f * rnd.nextFloat())
                colours(pal, j, RING_COUNT, cc)
                push(sp.x, sp.y, cos(th) * v, sin(th) * v * (if (flat) tilt else 1f), sp.life * (0.8f + 0.25f * rnd.nextFloat()) * (if (vk > 1f) 1.15f else 0.9f),
                    cc[0], cc[1], sp.size * 0.85f, sp.trail * (if (vk > 1f) 1.4f else 1f), sp.twinkle * 0.5f, sp.gravity, sp.drag, 0f, K_SPARK)
            }
        }
        // inner core
        val coreN = 16
        when (rec.core) {
            FwCore.CRACKLE -> repeat(coreN) { val th = rnd.nextFloat() * 6.283f; val v = sp.speed * (0.12f + 0.2f * rnd.nextFloat())
                push(sp.x, sp.y, cos(th) * v, sin(th) * v, 0.55f + 0.4f * rnd.nextFloat(), Color.WHITE, FwPalette.GOLD.a, sp.size * 0.8f, 0.02f, 0.4f, sp.gravity * 0.5f, 2f, 0f, K_CRACKLE) }
            FwCore.GLITTER -> repeat(coreN) { val th = rnd.nextFloat() * 6.283f; val v = sp.speed * (0.1f + 0.3f * rnd.nextFloat())
                push(sp.x, sp.y, cos(th) * v, sin(th) * v, 1.1f + 0.5f * rnd.nextFloat(), FwPalette.GOLD.a, FwPalette.SILVER.a, sp.size * 0.7f, 0.02f, 1f, sp.gravity, 1.8f, 0f, K_GLITTER) }
            FwCore.STROBE -> repeat(coreN) { val th = rnd.nextFloat() * 6.283f; val v = sp.speed * (0.2f + 0.3f * rnd.nextFloat())
                push(sp.x, sp.y, cos(th) * v, sin(th) * v, 1.2f + 0.4f * rnd.nextFloat(), Color.WHITE, FwPalette.SILVER.b, sp.size * 0.9f, 0f, 1f, sp.gravity * 0.6f, 1.6f, 0f, K_STROBE) }
            FwCore.NONE -> Unit
        }
        // outer layer
        when (rec.outer) {
            FwOuter.WILLOW -> repeat(18) { val th = rnd.nextFloat() * 6.283f; val v = sp.speed * (0.7f + 0.3f * rnd.nextFloat())
                push(sp.x, sp.y, cos(th) * v, sin(th) * v, 2.2f + 0.4f * rnd.nextFloat(), FwPalette.GOLD.a, FwPalette.GOLD.b, sp.size * 0.8f, 0.16f, 0.3f, 130f * sp.size / 2f, 0.9f, 0f, K_SPARK) }
            FwOuter.CROSSETTE -> repeat(7) { val th = rot + it * 6.283f / 7f; val v = sp.speed * 0.95f
                push(sp.x, sp.y, cos(th) * v, sin(th) * v, 0.55f, sp.ringPalettes.last().a, sp.ringPalettes.last().b, sp.size * 1.2f, 0.06f, 0f, sp.gravity, sp.drag, 0f, K_CROSS) }
            FwOuter.COMET -> repeat(6) { val th = rot + it * 6.283f / 6f + 0.3f; val v = sp.speed * 1.1f
                push(sp.x, sp.y, cos(th) * v, sin(th) * v, 1.6f, Color.WHITE, sp.palette.b, sp.size * 2f, 0.22f, 0.1f, sp.gravity * 1.4f, 0.8f, 0f, K_SPARK) }
            FwOuter.HORSETAIL -> repeat(16) { val th = -PI.toFloat() / 2f + (rnd.nextFloat() - 0.5f) * 1.6f; val v = sp.speed * (0.5f + 0.4f * rnd.nextFloat())
                push(sp.x, sp.y, cos(th) * v, sin(th) * v, 2.0f, FwPalette.SILVER.a, FwPalette.GOLD.b, sp.size * 0.8f, 0.16f, 0.2f, 160f * sp.size / 2f, 0.9f, 0f, K_SPARK) }
            FwOuter.NONE -> Unit
        }
        // twinkle stars scattered around it
        repeat(TWINKLES) { val th = rnd.nextFloat() * 6.283f; val r = sp.speed * (0.4f + 0.9f * rnd.nextFloat()) * 0.7f
            push(sp.x + cos(th) * r, sp.y + sin(th) * r, 0f, 6f, 0.6f + 0.9f * rnd.nextFloat(), Color.WHITE, sp.palette.a, sp.size * 1.6f, 0f, 1f, 0f, 0f, 0f, K_TWINKLE) }
        // a bright flash at the centre
        push(sp.x, sp.y, 0f, 0f, 0.18f, Color.WHITE, sp.palette.a, sp.size * 7f, 0f, 0f, 0f, 0f, 0f, K_FLASH)
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        // rising shells: thrust up along a wobbly rocket path, a dotted glitter trail, burst at the end of the flight
        for (q in 0 until maxQ) if (qT[q] >= 0f) {
            val sp = qSpec[q] ?: run { qT[q] = -1f; null } ?: continue
            qT[q] += dt
            val ft = qT[q] - qDelay[q]
            if (ft < 0f) continue
            if (ft >= qFlight[q]) { qT[q] = -1f; qSpec[q] = null; burst(sp); continue }
            val s = qS[q]
            val k = (ft / qFlight[q]).coerceIn(0f, 1f)
            // slowing as it climbs (a rocket losing thrust: speed factor integrates to 1 over the flight), weaving side to side
            val slow = 1.6f - 1.2f * k
            val wob = sin(ft * 13f + qWob[q]) * 70f * s * (1f - k)
            qX[q] += (qVX[q] * slow + wob) * dt
            qY[q] += qVY[q] * slow * dt
            // dotted glittering trail
            if (count < cap - 40 || rnd.nextFloat() < 0.3f) {
                push(qX[q] + (rnd.nextFloat() - 0.5f) * 3f * s, qY[q], (rnd.nextFloat() - 0.5f) * 14f * s, 10f * s + rnd.nextFloat() * 20f * s,
                    0.5f + 0.5f * rnd.nextFloat(), Color.rgb(0xFF, 0xF0, 0xB0), Color.rgb(0xFF, 0x80, 0x20), (0.7f + 0.5f * rnd.nextFloat()) * s, 0f, 0.9f, 30f * s, 1.5f, 0f, K_TRAIL)
            }
        }
        var i = 0
        while (i < count) {
            age[i] += dt
            val kd = kind[i]
            if (kd == K_CROSS && age[i] >= life[i]) {
                // crossette: splits into a little cross of 4
                val px = x[i]; val py = y[i]; val a = c0[i]; val b = c1[i]; val s0 = sz[i]; val h0 = atan2(vy[i], vx[i])
                remove(i)
                for (j in 0 until 4) { val th = h0 + 0.785f + j * 1.5708f; val v = 70f + rnd.nextFloat() * 20f
                    push(px, py, cos(th) * v * s0 / 2f, sin(th) * v * s0 / 2f, 0.6f, a, b, s0 * 0.8f, 0.08f, 0.2f, 40f, 1.8f, 0f, K_SPARK) }
                continue
            }
            if (age[i] >= life[i]) {
                if (kd == K_STAGE || kd == K_CRACKLE) {
                    // multi-stage re-burst / crackle pop (still capped by the pool)
                    val px = x[i]; val py = y[i]; val a = c1[i]; val b = c0[i]; val s0 = sz[i]
                    remove(i)
                    val m = if (kd == K_STAGE) 5 else 3
                    for (j in 0 until m) {
                        val th = j * 6.283f / m + rnd.nextFloat() * 0.4f; val v = (if (kd == K_STAGE) 45f else 25f) + rnd.nextFloat() * 35f
                        push(px, py, cos(th) * v, sin(th) * v, if (kd == K_STAGE) 0.45f else 0.18f, if (kd == K_CRACKLE) Color.WHITE else a, b,
                            s0 * 0.8f, 0.03f, 0.6f, 50f, 2.2f, 0f, K_SPARK)
                    }
                    continue
                }
                remove(i); continue
            }
            val d = exp1(-drg[i] * dt)
            vx[i] *= d; vy[i] = vy[i] * d + grav[i] * dt
            if (spin[i] != 0f) {
                val a = spin[i] * dt; val c = cos(a); val s = sin(a)
                val nx = vx[i] * c - vy[i] * s; vy[i] = vx[i] * s + vy[i] * c; vx[i] = nx
            }
            x[i] += vx[i] * dt; y[i] += vy[i] * dt
            i++
        }
    }

    private fun remove(i: Int) {
        val l = count - 1
        x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]; age[i] = age[l]; life[i] = life[l]; c0[i] = c0[l]; c1[i] = c1[l]
        sz[i] = sz[l]; tr[i] = tr[l]; tw[i] = tw[l]; grav[i] = grav[l]; drg[i] = drg[l]; spin[i] = spin[l]; kind[i] = kind[l]
        count--
    }

    fun draw(c: Canvas) {
        // the rising shells: a bright head (a tiny rocket glow)
        for (q in 0 until maxQ) if (qT[q] >= qDelay[q] && qSpec[q] != null) {
            val s = qS[q]
            fill.color = Color.rgb(0xFF, 0xD0, 0x60); fill.alpha = 90; c.drawCircle(qX[q], qY[q], 5f * s, fill)
            fill.color = Color.WHITE; fill.alpha = 255; c.drawCircle(qX[q], qY[q], 1.8f * s, fill)
        }
        for (i in 0 until count) {
            val f = age[i] / life[i]
            val col = mix(c0[i], c1[i], f)
            var a = if (f < 0.7f) 1f else (1f - f) / 0.3f
            val kd = kind[i]
            if (tw[i] > 0f) {
                val fl = sin(age[i] * (26f + 14f * (i % 5)) + i)
                a *= 1f - tw[i] * 0.5f * (1f + fl) * (if ((kd == K_GLITTER || kd == K_TRAIL) && f > 0.3f) 1f else 0.6f)
            }
            if (kd == K_STROBE && sin(age[i] * 38f + i) < 0f) a *= 0.08f
            if (a <= 0.02f) continue
            val al = (255 * a.coerceIn(0f, 1f)).toInt()
            when (kd) {
                K_FLASH -> { fill.color = col; fill.alpha = (al * 0.7f).toInt(); c.drawCircle(x[i], y[i], sz[i] * (0.6f + f), fill); continue }
                K_TWINKLE -> {
                    val r = sz[i] * (0.6f + 0.6f * abs(sin(age[i] * 9f + i)))
                    stroke.color = col; stroke.alpha = al; stroke.strokeWidth = sz[i] * 0.25f
                    c.drawLine(x[i] - r, y[i], x[i] + r, y[i], stroke); c.drawLine(x[i], y[i] - r, x[i], y[i] + r, stroke)
                    continue
                }
                K_TRAIL -> { fill.color = col; fill.alpha = al; c.drawCircle(x[i], y[i], sz[i], fill); continue }
            }
            if (tr[i] > 0f) {
                // a tapered streak: a thin faint tail, then a thicker bright head
                val tx = x[i] - vx[i] * tr[i]; val ty = y[i] - vy[i] * tr[i]
                stroke.color = col; stroke.alpha = (al * 0.45f).toInt(); stroke.strokeWidth = sz[i] * 0.45f
                c.drawLine(tx, ty, x[i], y[i], stroke)
                val mx = x[i] - vx[i] * tr[i] * 0.4f; val my = y[i] - vy[i] * tr[i] * 0.4f
                stroke.alpha = (al * 0.9f).toInt(); stroke.strokeWidth = sz[i] * 0.85f
                c.drawLine(mx, my, x[i], y[i], stroke)
            }
            fill.color = mix(col, Color.WHITE, 0.35f); fill.alpha = al
            c.drawCircle(x[i], y[i], sz[i] * (if (kd == K_GLITTER) 0.7f else 0.75f), fill)
        }
        fill.alpha = 255; stroke.alpha = 255
    }

    companion object {
        /** v10.9: 600 live sparks (was 300), pooled. */
        const val CAP = 600
        const val MAX_PER_BURST = 90
        /** Sparks in each extra concentric ring layer. */
        const val RING_COUNT = 28
        const val TWINKLES = 6
        /** Seconds a shell takes to rise before it bursts (minimum). */
        const val RISE = 0.55f
        private const val K_SPARK = 0; private const val K_GLITTER = 1; private const val K_STAGE = 2; private const val K_FLASH = 3
        private const val K_CROSS = 4; private const val K_STROBE = 5; private const val K_CRACKLE = 6; private const val K_TWINKLE = 7; private const val K_TRAIL = 8

        private fun exp1(v: Float) = kotlin.math.exp(v)
        private fun hue(h0: Float): Int {
            val h = ((h0 % 6f) + 6f) % 6f
            val x = 1f - abs(h % 2f - 1f)
            val (r, g, b) = when (h.toInt()) { 0 -> Triple(1f, x, 0f); 1 -> Triple(x, 1f, 0f); 2 -> Triple(0f, 1f, x); 3 -> Triple(0f, x, 1f); 4 -> Triple(x, 0f, 1f); else -> Triple(1f, 0f, x) }
            return Color.rgb((150 + 105 * r).toInt(), (150 + 105 * g).toInt(), (150 + 105 * b).toInt())
        }
        fun mix(a: Int, b: Int, k0: Float): Int {
            val k = k0.coerceIn(0f, 1f)
            return Color.rgb((Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt())
        }
        @Suppress("unused") private fun len(x: Float, y: Float) = hypot(x, y)
    }
}
