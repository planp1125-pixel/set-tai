package com.combo.monkeycompanion.brain

import com.combo.monkeycompanion.speech.Lines
import kotlin.math.hypot
import kotlin.random.Random

/**
 * v3.2 Superpowers: data-driven, so adding a power later is one new object in [Superpower]
 * plus one entry in [Superpowers.all]. A power is a set of visual kits ([PowerKit], which the
 * renderer and particle emitter already know how to draw) and a flight plan made of
 * [PowerStep]s (which the shared [PowerFlight] engine flies). Everything here is original:
 * no comic-book names, logos or costumes.
 */
enum class PowerKit {
    /** Flame jets from both feet (+ flame / smoke particles, sputter when fuel is low). */
    FOOT_JETS,
    /** Glowing rings fired from the palms during [PowerPhase.FIRE]. */
    PALM_RINGS,
    /** A red cape on a verlet chain that flaps in the wind. */
    CAPE,
    /** Speed lines streaming behind while flying fast. */
    SPEED_LINES,
    /** A little banana badge on the chest. */
    BADGE,
    // v4 show-off kits
    /** A wooden staff that crackles with lightning. */
    STAFF,
    /** A ninja headband + smoke bombs. */
    NINJA,
    /** Icy blue tint, icicles and snowflakes. */
    FROST,
    /** Top hat + wand (and a rabbit inside). */
    HAT,
}

enum class PowerPhase {
    TAKEOFF, FLY, FIRE, SPUTTER, LAND, CRASH, POSE,
    // v4 show-off phases (all stay in place except TELEPORT, which jumps halfway through)
    CHARGE, STRIKE, TELEPORT, CLONES, GROW, SLAM, SHRINK, FROST, PEEK, DODGE, MAGIC,
    // v4.1 hero phases (stay in place except BLUR, which zips to its target)
    LASER, BREATH, LIFT, BLUR, CONFETTI, SHIELD, DROOP;

    /** Needs the full-screen effects layer (lightning, crack, frost, giant eye, slow-mo). */
    val screenFx: Boolean get() = this == STRIKE || this == SLAM || this == SHRINK || this == FROST || this == PEEK || this == DODGE || this == CHARGE ||
        this == LASER || this == BREATH || this == LIFT || this == BLUR
}

/**
 * One leg of a routine. Targets are screen fractions: x 0 = left edge, 1 = right edge (box left
 * over the usable width); y 0 = standing on the floor, 1 = the top. NaN = stay where it is.
 * [bend] curves the path sideways (fraction of the segment length), for swoops and loops.
 */
class PowerStep(val phase: PowerPhase, val secs: Float, val tx: Float = Float.NaN, val ty: Float = Float.NaN, val bend: Float = 0f)

sealed class Superpower(
    val id: String,
    val title: String,
    val blurb: String,
    val kits: Set<PowerKit>,
) {
    /** A flight plan from (x0, y0). [crash] = the comedy variant (sputter / crash into the screen edge). */
    abstract fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep>
    abstract val startLines: List<String>
    abstract val landLines: List<String>
    open val crashLines: List<String> get() = Lines.POWER_CRASH
    open val fireLines: List<String> get() = Lines.POWER_FIRE
    /** Chance of the comedy crash variant. */
    open val crashChance: Float get() = 0.3f
    /** v4.1: false = only triggered by an event (e.g. Armour Anna's low-battery droop), never at random. */
    open val randomPick: Boolean get() = true
    fun has(k: PowerKit) = k in kits

    /** Jet boots, swoops, palm rings, thud landing. Crash variant: sputtering engine, splat, sooty face. */
    object RocketMonkey : Superpower(
        "rocket", "Rocket Monkey", "Flame jets from both feet, swooping flight, glowing palm rings, thud landing",
        setOf(PowerKit.FOOT_JETS, PowerKit.PALM_RINGS),
    ) {
        override val startLines get() = Lines.POWER_ROCKET
        override val landLines get() = Lines.POWER_LAND_ROCKET
        override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
            val a = if (x0 < 0.5f) 0.72f + rnd.nextFloat() * 0.2f else 0.08f + rnd.nextFloat() * 0.2f
            val b = 1f - a
            val first = listOf(
                PowerStep(PowerPhase.TAKEOFF, 0.8f, x0, 0.16f),
                PowerStep(PowerPhase.FLY, 1.5f, a, 0.62f + rnd.nextFloat() * 0.12f, 0.35f),
                PowerStep(PowerPhase.FIRE, 1.1f),
                PowerStep(PowerPhase.FLY, 1.4f, b, 0.42f + rnd.nextFloat() * 0.1f, -0.4f),
            )
            return if (!crash) first + listOf(
                PowerStep(PowerPhase.FIRE, 0.9f),
                PowerStep(PowerPhase.FLY, 1.3f, 0.3f + rnd.nextFloat() * 0.4f, 0.12f, 0.3f),
                PowerStep(PowerPhase.LAND, 0.9f, Float.NaN, 0f),
            ) else {
                val edge = if (b < 0.5f) 1f else 0f
                first + listOf(
                    PowerStep(PowerPhase.SPUTTER, 1.7f, if (edge > 0.5f) 0.8f else 0.2f, 0.5f, 0.15f),
                    PowerStep(PowerPhase.CRASH, 2.1f, edge),
                    PowerStep(PowerPhase.POSE, 1.7f),
                )
            }
        }
    }

    // ---------------- v4 show-off powers (original designs) ----------------

    /** Lightning staff: charges up (sparks, hair on end), then a bolt cracks across the screen. Crash: the bolt comes back down on him. */
    object ThunderMonkey : Superpower("thunder", "Thunder Monkey", "Charges a lightning staff and strikes a bolt across the screen", setOf(PowerKit.STAFF)) {
        override val startLines get() = Lines.POWER_THUNDER
        override val landLines get() = Lines.POWER_LAND_THUNDER
        override val crashLines get() = Lines.POWER_THUNDER_ZAP
        override val crashChance get() = 0.2f
        override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
            PowerStep(PowerPhase.TAKEOFF, 0.5f, x0, 0.06f),
            PowerStep(PowerPhase.CHARGE, 1.5f),
            PowerStep(PowerPhase.STRIKE, 0.9f),
            PowerStep(PowerPhase.CHARGE, 0.8f),
            PowerStep(PowerPhase.STRIKE, 0.9f),
            PowerStep(PowerPhase.LAND, 0.5f, Float.NaN, 0f),
            PowerStep(PowerPhase.POSE, 1.6f),
        )
    }

    /** Smoke-bomb teleports between screen corners, then three shadow clones dance in sync. Crash: teleports into his own smoke. */
    object NinjaSettai : Superpower("ninja", "Ninja Settai", "Smoke-bomb teleports between corners, three shadow clones dance", setOf(PowerKit.NINJA)) {
        override val startLines get() = Lines.POWER_NINJA
        override val landLines get() = Lines.POWER_LAND_NINJA
        override val crashChance get() = 0.15f
        override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
            val far = if (x0 < 0.5f) 1f else 0f
            return listOf(
                PowerStep(PowerPhase.TELEPORT, 0.9f, far, 0f),
                PowerStep(PowerPhase.TELEPORT, 0.9f, 1f - far, 0f),
                PowerStep(PowerPhase.TELEPORT, 0.9f, 0.5f, 0f),
                PowerStep(PowerPhase.CLONES, 3.4f),
                PowerStep(PowerPhase.TELEPORT, 0.9f, 0.25f + rnd.nextFloat() * 0.5f, 0f),
                PowerStep(PowerPhase.POSE, 1.4f),
            )
        }
    }

    /** "Kovam" (anger): swells up huge, pounds the screen (fake crack), then shrinks tiny and embarrassed. */
    object KovamMode : Superpower("kovam", "Kovam Mode", "Grows huge when angry, cracks the screen (not really), shrinks embarrassed", emptySet()) {
        override val startLines get() = Lines.POWER_KOVAM
        override val landLines get() = Lines.POWER_LAND_KOVAM
        override val crashChance get() = 0f
        override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
            PowerStep(PowerPhase.GROW, 1.5f, x0.coerceIn(0.15f, 0.85f), 0f),
            PowerStep(PowerPhase.SLAM, 0.9f),
            PowerStep(PowerPhase.SHRINK, 2.2f),
            PowerStep(PowerPhase.POSE, 1.4f),
        )
    }

    /** Frost creeps in from the screen edges, snow falls, Settai shivers. Runs by itself when the phone is hot. */
    object IceMonkey : Superpower("ice", "Ice Monkey", "Frosty screen edges and snow to cool a hot phone", setOf(PowerKit.FROST)) {
        override val startLines get() = Lines.POWER_ICE
        override val landLines get() = Lines.POWER_LAND_ICE
        override val crashChance get() = 0f
        override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
            PowerStep(PowerPhase.FROST, 4.2f, x0, 0f),
            PowerStep(PowerPhase.POSE, 1.4f),
        )
    }

    /** Settai "becomes a giant": only one huge eye peeks in from a screen edge, looks around, blinks. */
    object GiantMode : Superpower("giant", "Giant Mode", "Only a huge eye peeks in from the edge of the screen", emptySet()) {
        override val startLines get() = Lines.POWER_GIANT
        override val landLines get() = Lines.POWER_LAND_GIANT
        override val crashChance get() = 0f
        override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
            PowerStep(PowerPhase.PEEK, 4.6f, x0, 0f),
            PowerStep(PowerPhase.POSE, 1.3f),
        )
    }

    /** Time Freeze: the world goes slow-motion, bananas fly in, Settai bends back and dodges with after-images. */
    object TimeFreeze : Superpower("freeze", "Time Freeze", "Slow-motion banana dodge with after-images", emptySet()) {
        override val startLines get() = Lines.POWER_FREEZE
        override val landLines get() = Lines.POWER_LAND_FREEZE
        override val crashLines get() = Lines.POWER_FREEZE_HIT
        override val crashChance get() = 0.2f
        override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
            PowerStep(PowerPhase.DODGE, 4.4f, x0, 0f),
            PowerStep(PowerPhase.POSE, 1.4f),
        )
    }

    /** Magician: taps a top hat, sparkles, and a rabbit pops out holding up your notification count. */
    object Magician : Superpower("magic", "Magician", "Pulls a rabbit out of a hat, holding up your notification count", setOf(PowerKit.HAT)) {
        override val startLines get() = Lines.POWER_MAGIC
        override val landLines get() = Lines.POWER_LAND_MAGIC
        override val crashChance get() = 0.15f
        override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
            PowerStep(PowerPhase.MAGIC, 4.0f, x0, 0f),
            PowerStep(PowerPhase.POSE, 1.4f),
        )
    }

    /** Cape, fist-first flight with speed lines, heroic landing pose. Crash variant: face-first into the edge. */
    object SuperBanana : Superpower(
        "banana", "Super Banana", "Red flapping cape, fist-first flight with speed lines, heroic landing pose",
        setOf(PowerKit.CAPE, PowerKit.SPEED_LINES, PowerKit.BADGE),
    ) {
        override val startLines get() = Lines.POWER_BANANA
        override val landLines get() = Lines.POWER_LAND_BANANA
        override val crashChance get() = 0.25f
        override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
            val a = if (x0 < 0.5f) 0.75f + rnd.nextFloat() * 0.2f else 0.05f + rnd.nextFloat() * 0.2f
            val first = listOf(
                PowerStep(PowerPhase.TAKEOFF, 0.6f, x0, 0.2f),
                PowerStep(PowerPhase.FLY, 1.3f, a, 0.72f + rnd.nextFloat() * 0.1f, 0.3f),
                PowerStep(PowerPhase.FLY, 1.4f, 1f - a, 0.5f + rnd.nextFloat() * 0.1f, -0.45f),
            )
            return if (!crash) first + listOf(
                PowerStep(PowerPhase.FLY, 1.2f, 0.35f + rnd.nextFloat() * 0.3f, 0.14f, 0.3f),
                PowerStep(PowerPhase.LAND, 0.6f, Float.NaN, 0f),
                PowerStep(PowerPhase.POSE, 1.9f),
            ) else first + listOf(
                PowerStep(PowerPhase.CRASH, 2.1f, if (a > 0.5f) 0f else 1f),
                PowerStep(PowerPhase.POSE, 1.6f),
            )
        }
    }
}

object Superpowers {
    /** Every power. Add new ones here (and as an object in [Superpower]); Settings and the AI pick them up. */
    val monkey: List<Superpower> by lazy {
        listOf(Superpower.RocketMonkey, Superpower.SuperBanana, Superpower.ThunderMonkey, Superpower.NinjaSettai, Superpower.KovamMode,
            Superpower.IceMonkey, Superpower.GiantMode, Superpower.TimeFreeze, Superpower.Magician)
    }
    /** v4.1: the current character's powers (Settings and random picks use these). */
    val all: List<Superpower> get() = PowerPrefs.enabled(com.combo.monkeycompanion.character.CharacterRegistry.current)   // v10.17: the picker's choice
    /** Every power of every character. */
    val everything: List<Superpower> get() = com.combo.monkeycompanion.character.CharacterRegistry.all.flatMap { it.powers }.distinct()

    /** A power named in a chat message ("do the ninja thing", "thunder power"), else null. */
    fun named(text: String): Superpower? {
        val t = text.lowercase()
        return when {
            Regex("\\b(heat ?vision|laser( eyes)?)\\b").containsMatchIn(t) -> HeatVisionPower
            Regex("\\b(super ?breath|frost breath|blow)\\b").containsMatchIn(t) -> SuperBreathPower
            Regex("\\b(super ?strength|lift|strong)\\b").containsMatchIn(t) -> SuperStrengthPower
            Regex("\\b(speed|super ?speed|zoom|blur)\\b").containsMatchIn(t) -> SpeedBlurPower
            Regex("\\b(fireworks?|sky ?burst|pataka|cracker)\\b").containsMatchIn(t) -> SkyBurstPower
            Regex("\\b(boost|full throttle)\\b").containsMatchIn(t) -> RocketBoostPower
            Regex("\\b(confetti|mini ?rockets?|party)\\b").containsMatchIn(t) -> ConfettiRocketsPower
            Regex("\\b(shield|force ?field|bubble)\\b").containsMatchIn(t) -> ShieldBubblePower
            Regex("\\b(jets?|armou?r flight|suit up)\\b").containsMatchIn(t) -> ArmourJetsPower
            Regex("\\b(rocket|jet ?boots?)\\b").containsMatchIn(t) -> Superpower.RocketMonkey
            Regex("\\b(cape|super ?banana)\\b").containsMatchIn(t) -> Superpower.SuperBanana
            Regex("\\b(thunder|lightning|staff|minnal|idi)\\b").containsMatchIn(t) -> Superpower.ThunderMonkey
            Regex("\\b(ninja|clones?|teleport|smoke bomb)\\b").containsMatchIn(t) -> Superpower.NinjaSettai
            Regex("\\b(kovam|angry mode|hulk out|rage|get angry|grow huge)\\b").containsMatchIn(t) -> Superpower.KovamMode
            Regex("\\b(ice|frost|freeze the screen|cool (it|the phone) down|snow)\\b").containsMatchIn(t) -> Superpower.IceMonkey
            Regex("\\b(giant|huge eye|big eye)\\b").containsMatchIn(t) -> Superpower.GiantMode
            Regex("\\b(time freeze|slow ?mo(tion)?|matrix|dodge)\\b").containsMatchIn(t) -> Superpower.TimeFreeze
            Regex("\\b(magic|magician|rabbit|hat trick)\\b").containsMatchIn(t) -> Superpower.Magician
            else -> null
        }
    }

    /** Set when the user named a power; the next [choose] uses it once. */
    @Volatile var requested: Superpower? = null
    const val OFF = "off"
    const val RANDOM = "random"

    fun byId(id: String?): Superpower? = all.firstOrNull { it.id == id } ?: everything.firstOrNull { it.id == id }

    /** The power to use for setting [setting] ("off", "random" or a power id). Null = powers off. */
    fun choose(setting: String, rnd: Random = Random): Superpower? {
        val asked = requested; requested = null
        if (setting == OFF) return null
        return asked?.takeIf { it in all } ?: pick(setting, rnd)
    }

    private fun pick(setting: String, rnd: Random): Superpower? = when (setting) {
        OFF -> null
        RANDOM -> all.filter { it.randomPick }.random(rnd)
        else -> all.firstOrNull { it.id == setting } ?: all.filter { it.randomPick }.random(rnd)
    }
}

/**
 * Flies a [Superpower] plan. Pure math in screen fractions (see [PowerStep]); the host turns
 * (x, y) into pixels. No allocation per frame.
 */
class PowerFlight {
    var power: Superpower? = null; private set
    var crash = false; private set
    private var steps: List<PowerStep> = emptyList()
    var index = 0; private set
    /** Seconds into the current step / the whole routine. */
    var stepT = 0f; private set
    var time = 0f; private set
    var x = 0f; private set
    var y = 0f; private set
    private var sx = 0f
    private var sy = 0f

    val active get() = power != null
    val step: PowerStep? get() = steps.getOrNull(index)
    val phase: PowerPhase get() = step?.phase ?: PowerPhase.POSE
    val k: Float get() = step?.let { (stepT / it.secs).coerceIn(0f, 1f) } ?: 1f
    /** How the routine ends (a LAND-ending routine says its landing line at the thud). */
    val lastPhase: PowerPhase? get() = steps.lastOrNull()?.phase
    val totalSeconds: Float get() = steps.fold(0f) { a, s -> a + s.secs }
    /** Did the routine go through [PowerPhase.CRASH] (sooty face for the rest of it). */
    var crashed = false; private set

    /** The step where a crash-less comedy variant goes wrong: the last one before the final POSE. */
    private var lastBigIndex = -1

    fun begin(p: Superpower, plan: List<PowerStep>, crash: Boolean, x0: Float, y0: Float) {
        power = p; steps = plan; this.crash = crash
        lastBigIndex = if (plan.any { it.phase == PowerPhase.CRASH }) -1 else plan.indexOfLast { it.phase != PowerPhase.POSE && it.phase != PowerPhase.LAND }
        index = 0; stepT = 0f; time = 0f; crashed = false
        x = x0; y = y0; sx = x0; sy = y0
    }

    fun cancel() { power = null }

    /** Advance; false once the routine is over (then [power] is null). */
    fun update(dt: Float): Boolean {
        if (power == null) return false
        var s = step ?: run { power = null; return false }
        stepT += dt; time += dt
        while (stepT >= s.secs) {
            place(s, 1f)
            stepT -= s.secs; index++; sx = x; sy = y
            s = step ?: run { power = null; return false }
        }
        if (s.phase == PowerPhase.CRASH && stepT / s.secs > CRASH_HIT) crashed = true
        // v4: comedy variants without a CRASH leg (zapped by his own bolt, hit by a banana, wrong rabbit...).
        if (crash && (power?.crashChance ?: 0f) > 0f && s.phase != PowerPhase.CRASH && index == lastBigIndex && stepT / s.secs > 0.45f) crashed = true
        place(s, stepT / s.secs)
        return true
    }

    private fun place(s: PowerStep, k: Float) {
        val tx = if (s.tx.isNaN()) sx else s.tx
        val ty = if (s.ty.isNaN()) sy else s.ty
        when (s.phase) {
            PowerPhase.FIRE, PowerPhase.POSE, PowerPhase.CHARGE, PowerPhase.STRIKE, PowerPhase.CLONES, PowerPhase.SLAM,
            PowerPhase.SHRINK, PowerPhase.PEEK, PowerPhase.DODGE, PowerPhase.MAGIC -> { x = sx; y = sy }
            PowerPhase.TELEPORT -> if (k < 0.5f) { x = sx; y = sy } else { x = tx; y = ty }
            PowerPhase.LASER, PowerPhase.BREATH, PowerPhase.LIFT, PowerPhase.CONFETTI, PowerPhase.SHIELD, PowerPhase.DROOP -> { x = sx; y = sy }
            PowerPhase.BLUR -> { val e = smooth(k); x = sx + (tx - sx) * e; y = sy + (ty - sy) * e }
            PowerPhase.GROW, PowerPhase.FROST -> { val e = smooth(k * 3f); x = sx + (tx - sx) * e; y = sy + (ty - sy) * e }
            PowerPhase.CRASH -> when {
                k < CRASH_HIT -> { val e = k / CRASH_HIT; x = sx + (tx - sx) * e * e; y = sy + (ty - sy) * e }
                k < CRASH_SLIDE -> { x = tx; y = ty }
                else -> { val e = smooth((k - CRASH_SLIDE) / (1f - CRASH_SLIDE)); x = tx; y = ty * (1f - e) }
            }
            else -> {
                val e = when (s.phase) {
                    PowerPhase.TAKEOFF -> k * k
                    PowerPhase.LAND -> 1f - (1f - k) * (1f - k)
                    else -> 0.5f * k + 0.5f * smooth(k)
                }
                // Quadratic bezier with a sideways control point: swoops.
                val dx = tx - sx; val dy = ty - sy
                val len = hypot(dx, dy)
                val cx = (sx + tx) / 2f - dy * s.bend * (if (len > 0f) 1f else 0f)
                val cy = (sy + ty) / 2f + dx * s.bend * (if (len > 0f) 1f else 0f)
                val u = 1f - e
                x = u * u * sx + 2f * u * e * cx + e * e * tx
                y = u * u * sy + 2f * u * e * cy + e * e * ty
                if (s.phase == PowerPhase.SPUTTER) y += 0.025f * kotlin.math.sin(k * 38f) * (1f - k * 0.5f)
            }
        }
        x = x.coerceIn(0f, 1f); y = y.coerceIn(0f, 1f)
    }

    private fun smooth(k: Float): Float { val c = k.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

    companion object {
        const val CRASH_HIT = 0.16f
        const val CRASH_SLIDE = 0.55f
    }
}
