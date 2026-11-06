package com.combo.monkeycompanion.engine.script

import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.action.ActionLibrary
import com.combo.monkeycompanion.engine.expression.Expressions
import com.combo.monkeycompanion.engine.movement.Edge
import com.combo.monkeycompanion.engine.movement.Mover
import com.combo.monkeycompanion.engine.props.PropKind
import com.combo.monkeycompanion.engine.props.Projectile
import com.combo.monkeycompanion.engine.props.Props
import com.combo.monkeycompanion.engine.stagefx.RayColors
import com.combo.monkeycompanion.engine.stagefx.StageFx
import kotlin.math.hypot
import kotlin.math.sin

/**
 * v10.32 Script player: runs a [ShowScript] for ANY cast (built-in or studio character ids). Every verb is generic:
 * movement via [Mover] (respecting the body's locomotion), faces via [Expressions], body actions via
 * [ActionLibrary], props / projectiles via [Props], beams / clash / crack via [StageFx]. There is no character-id
 * branch anywhere in here. Deterministic: a pure function of the script and the time steps (no Random).
 * Coordinates are screen fractions; [aspect] = screen height / width (for round distances).
 */
class ScriptPlayer(val script: ShowScript, val aspect: Float = 2.17f) {
    class Ray(val from: String, val src: String, val target: String, val color: Int, val t0: Float, val t1: Float)
    class Line(val text: String, val t0: Float, val t1: Float)

    inner class Actor(val role: String, val character: Character) {
        val mover = Mover.of(character)
        var face: String? = null; var faceT0 = 0f; var faceUntil = Float.MAX_VALUE
        var action: String? = null; var actionT0 = 0f; var actionUntil = Float.MAX_VALUE
        var held: PropKind? = null
        var line: Line? = null
        var lookAt: String? = null
        /** Body centre, screen fractions (the feet are at mover.y). */
        val cx get() = mover.x
        val cy get() = mover.y - 0.07f
        val visible get() = !mover.gone
        fun saying(t: Float) = line?.takeIf { t >= it.t0 && t < it.t1 }?.text
        fun faceAt(t: Float) = face?.takeIf { t < faceUntil }
        fun actionAt(t: Float) = action?.takeIf { t < actionUntil }
        /** -1 / 1: which way to look (null = no look target). */
        fun lookDir(): Float? {
            val tg = lookAt ?: return null
            val tx = when (tg) { "left" -> -10f; "right" -> 10f; "crack" -> crack?.cx ?: return null; "clash" -> clashX; else -> actors[tg]?.cx ?: return null }
            return if (tx >= cx) 1f else -1f
        }
    }

    private inner class Throwing(val owner: String, val kind: PropKind, val target: String, val every: Float, var left: Int, val speed: Float, var next: Float)

    val actors = LinkedHashMap<String, Actor>()
    val projectiles = ArrayList<Projectile>()
    val rays = ArrayList<Ray>()
    var crack: StageFx.Crack? = null; private set
    var crackT0 = 0f; private set
    var t = 0f; private set
    val done get() = t >= script.end
    /** Rules that already fired (index), and a log of what fired when (tests / debugging). */
    val fired = HashSet<Int>()
    val log = ArrayList<String>()

    // clash: two rays meet; the meeting point drifts, and can be dropped to the glass
    var clashA: String? = null; private set
    var clashB: String? = null; private set
    var clashT0 = 0f; private set
    var clashT1 = -1f; private set
    var clashX = 0.5f; private set
    var clashY = 0.5f; private set
    private var dropT0 = -1f; private var dropDur = 0.8f; private var dropFromY = 0.5f
    val clashOn get() = clashA != null && t < clashT1
    var shakeUntil = -1f; private set

    private val throwing = ArrayList<Throwing>()
    private var beatIdx = 0

    init {
        for ((role, id) in script.cast) {
            val ch = CharacterRegistry.byId(id) ?: throw ScriptError(0, "no character '$id' for role '$role'")
            actors[role] = Actor(role, ch).also { it.mover.place(if (actors.isEmpty()) 0.3f else 0.7f, Mover.FLOOR); it.mover.vanish(-10f, 0.01f); it.mover.step(0f) }
        }
    }

    fun actor(role: String) = actors[role]

    /** Advance the show to [to] seconds in small fixed steps (frame-rate independent, deterministic). */
    fun advanceTo(to: Float) { while (t < to) step(minOf(1f / 60f, to - t).coerceAtLeast(1e-4f)) }

    fun step(dt: Float) {
        t += dt
        while (beatIdx < script.beats.size && script.beats[beatIdx].at <= t) { script.beats[beatIdx].cmds.forEach { run(it) }; beatIdx++ }
        // throwers
        for (th in throwing) while (th.left > 0 && t >= th.next) { spawn(th); th.left--; th.next += th.every }
        throwing.removeAll { it.left <= 0 }
        for (a in actors.values) a.mover.step(t)
        // projectiles + proximity rules
        for (p in projectiles) {
            if (!p.alive) continue
            p.step(dt)
            script.rules.forEachIndexed { i, r ->
                if (i in fired || r.prop != p.kind.id) return@forEachIndexed
                val tg = actors[r.target] ?: return@forEachIndexed
                if (!tg.visible) return@forEachIndexed
                if (p.distTo(tg.cx, tg.cy, aspect) <= r.within) {
                    fired.add(i); log.add("%.2f when ${r.prop} near ${r.target}".format(t)); r.cmds.forEach { run(it) }
                }
            }
            // a projectile that reaches a visible actor it was aimed at pops; through empty air it flies on
            p.target?.let { actors[it] }?.let { tg -> if (tg.visible && p.distTo(tg.cx, tg.cy, aspect) < 0.03f) p.alive = false }
        }
        projectiles.removeAll { !it.alive }
        // clash point
        val ca = clashA?.let { actors[it] }; val cb = clashB?.let { actors[it] }
        if (ca != null && cb != null && t < clashT1) {
            if (dropT0 < 0f) {
                clashX = (ca.cx + cb.cx) / 2f + 0.03f * sin((t - clashT0) * 2.1f)
                clashY = (ca.cy + cb.cy) / 2f - 0.02f + 0.01f * sin((t - clashT0) * 3.3f)
            } else {
                val k = ((t - dropT0) / dropDur).coerceIn(0f, 1f)
                clashY = dropFromY + (Mover.FLOOR + 0.06f - dropFromY) * k * k
            }
        }
        rays.removeAll { t >= it.t1 }
    }

    private fun spawn(th: Throwing) {
        val o = actors[th.owner] ?: return; val tg = actors[th.target] ?: return
        val sx = o.cx + 0.05f * o.mover.facing; val sy = o.cy - 0.03f
        val dx = tg.cx - sx; val dy = (tg.cy - sy) * aspect
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        projectiles.add(Projectile(th.kind, th.owner, th.target, sx, sy, dx / len * th.speed, dy / len * th.speed / aspect, t))
    }

    private fun f(args: List<String>, i: Int, def: Float) = args.getOrNull(i)?.toFloatOrNull() ?: def
    /** value after keyword [k] ("in 2.5", "for 3"). */
    private fun kw(args: List<String>, k: String, def: Float): Float { val i = args.indexOfFirst { it.equals(k, true) }; return if (i >= 0) f(args, i + 1, def) else def }
    private fun kwS(args: List<String>, k: String): String? { val i = args.indexOfFirst { it.equals(k, true) }; return if (i >= 0) args.getOrNull(i + 1) else null }
    private fun xy(args: List<String>, after: String, a: Actor): Pair<Float, Float> {
        val i = args.indexOfFirst { it.equals(after, true) }
        return if (i >= 0) f(args, i + 1, a.mover.x) to f(args, i + 2, a.mover.y) else a.mover.x to a.mover.y
    }

    private fun run(c: Cmd) {
        if (c.subject == "stage") { stage(c); return }
        val who = if (c.subject == "both" || c.subject == "all") actors.values.toList() else listOfNotNull(actors[c.subject])
        for (a in who) actorCmd(a, c)
    }

    private fun stage(c: Cmd) {
        val a = c.args
        when (c.verb) {
            "clash" -> { clashA = a.getOrNull(0)?.lowercase(); clashB = a.getOrNull(1)?.lowercase(); clashT0 = t; clashT1 = t + kw(a, "for", 3f); dropT0 = -1f }
            "drop" -> { dropT0 = t; dropDur = kw(a, "in", 0.8f); dropFromY = clashY; clashT1 = maxOf(clashT1, t + dropDur) }
            "crack" -> {
                val (x, y) = if (a.getOrNull(1)?.lowercase() == "clash" || a.isEmpty()) clashX to clashY else f(a, 1, 0.5f) to f(a, 2, 0.6f)
                crack = StageFx.Crack(x, y, 7L + script.title.hashCode()); crackT0 = t; shakeUntil = t + 0.5f
            }
            "shake" -> shakeUntil = t + kw(a, "for", 0.5f)
        }
        log.add("%.2f stage ${c.verb}".format(t))
    }

    private fun actorCmd(a: Actor, c: Cmd) {
        val g = c.args
        val m = a.mover
        when (c.verb) {
            "place" -> { m.place(f(g, 0, m.x), f(g, 1, m.y)); m.appear(t, 0.01f) }
            "enter" -> { val e = edge(g.getOrNull(0)); val (x, y) = xy(g, "to", a); m.appear(t, 0.01f); m.enterFrom(t, e, x, y, kw(g, "in", 1.5f)) }
            "exit" -> m.exitTo(t, edge(g.getOrNull(0)), kw(g, "in", 1.2f))
            "move", "fly", "walk" -> { val (x, y) = xy(g, "to", a); m.moveTo(t, x, y, kw(g, "in", 1.2f)) }
            "hover" -> m.hover(t)
            "vanish" -> m.vanish(t, kw(g, "in", 0.6f))
            "appear" -> { if (g.firstOrNull()?.equals("at", true) == true) m.place(f(g, 1, m.x), f(g, 2, m.y)); m.appear(t, kw(g, "in", 0.6f)) }
            "face" -> { a.face = g.getOrNull(0)?.lowercase()?.takeIf { Expressions.byName(it) != null }; a.faceT0 = t; a.faceUntil = t + kw(g, "for", 1e6f) }
            "do" -> { a.action = g.getOrNull(0)?.lowercase()?.takeIf { ActionLibrary.byId(it) != null }; a.actionT0 = t; a.actionUntil = t + kw(g, "for", 1e6f) }
            "stop" -> { a.action = null }
            "look" -> { a.lookAt = g.getOrNull(0)?.lowercase()?.takeIf { it != "none" }; a.lookDir()?.let { m.facing = it } }
            "turn" -> m.facing = if (g.getOrNull(0)?.lowercase() == "left") -1f else 1f
            "say" -> a.line = Line(g.getOrNull(0) ?: "", t, t + kw(g, "for", 2f))
            "hold" -> a.held = g.getOrNull(0)?.let { Props.byId(it) }
            "drop" -> a.held = null
            "throw" -> {
                val k = g.getOrNull(0)?.let { Props.byId(it) } ?: return
                val target = kwS(g, "at")?.lowercase() ?: return
                if (target !in actors) return
                val n = g.indexOfFirst { it.startsWith("x") && it.drop(1).toIntOrNull() != null }.let { if (it >= 0) g[it].drop(1).toInt() else 1 }
                throwing.add(Throwing(a.role, k, target, kw(g, "every", 0.35f), n, kw(g, "speed", 1.2f), t))
            }
            "ray" -> {
                val src = g.getOrNull(0)?.lowercase() ?: "hands"
                val target = kwS(g, "to")?.lowercase() ?: return
                val color = RayColors.of(g.firstOrNull { RayColors.byName.containsKey(it.lowercase()) } ?: "red")
                rays.add(Ray(a.role, src, target, color, t, t + kw(g, "for", 1.5f)))
            }
        }
    }

    private fun edge(s: String?) = when (s?.lowercase()) { "right" -> Edge.RIGHT; "top" -> Edge.TOP; "bottom" -> Edge.BOTTOM; else -> Edge.LEFT }

    /** Where a ray from [r] ends now (screen fractions): the clash point while clashing, else its target. */
    fun rayEnd(r: Ray): Pair<Float, Float>? {
        if (clashOn && (r.from == clashA || r.from == clashB) && (r.target == clashA || r.target == clashB)) return clashX to clashY
        return when (r.target) { "crack" -> crack?.let { it.cx to it.cy }; "clash" -> clashX to clashY; else -> actors[r.target]?.let { it.cx to it.cy } }
    }
}
