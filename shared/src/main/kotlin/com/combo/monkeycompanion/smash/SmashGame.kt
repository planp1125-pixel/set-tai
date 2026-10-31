package com.combo.monkeycompanion.smash

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** How a character attacks a tile. [travelSecs] = flight time (0 = instant beam); [arc] = thrown in a curve. */
enum class Attack(val travelSecs: Float, val arc: Boolean) {
    LASER(0f, false), BLAST(0.16f, false), BALL(0.24f, true), WATER(0.2f, false), STONE(0.26f, true), PAN(0.24f, true), CHEESE(0.22f, true), ROCKET(0.3f, true);

    companion object {
        fun of(characterId: String): Attack = when (characterId) {
            "superboy" -> LASER
            "armour" -> BLAST
            "mask" -> BALL
            "gajju" -> WATER
            "billa" -> PAN
            "chittu" -> CHEESE
            "rocketgirl" -> ROCKET
            "surfer" -> WATER
            "hollowman" -> BALL
            else -> STONE
        }
    }
}

/**
 * v10.5 Screen Smash: a pretend copy of the screen cut into tiles that the character smashes. Each tile takes two hits
 * (crack, then shatter into flying shards); quick hits build a combo, and every [MEGA_EVERY] combo the next hit is a
 * mega smash that shatters a 3 x 3 block. v10.6: hit-stop on breaks, shards settle into a floor pile (capped).
 * Pure logic in screen px (no drawing, no Android), unit-tested.
 */
class SmashGame(
    val width: Float, val height: Float, val density: Float,
    val cols: Int = 4, val durationSecs: Float = 30f, private val rnd: Random = Random,
) {
    enum class State { PLAYING, OVER, REPAIR, DONE }

    val tileW = width / cols
    val rows = max(1, (height / tileW + 0.5f).toInt())
    val tileH = height / rows
    val tiles = rows * cols

    /** 2 = whole, 1 = cracked, 0 = shattered. */
    val hp = IntArray(tiles) { 2 }
    /** Where the crack started, 0..1 inside the tile; [crackSeed] picks the crack pattern. */
    val hitU = FloatArray(tiles); val hitV = FloatArray(tiles)
    val crackSeed = IntArray(tiles)
    /** Seconds since the tile shattered (for the glowing hole edge); < 0 = not shattered. */
    val brokenT = FloatArray(tiles) { -1f }

    var state = State.PLAYING; private set
    var timeLeft = durationSecs; private set
    var score = 0; private set
    var combo = 0; private set
    private var comboT = 0f
    /** The next impact is a mega smash. */
    var megaReady = false; private set
    var smashed = 0; private set
    /** 0..1 during [State.REPAIR]: the screen putting itself back together. */
    var repairK = 0f; private set

    /** Impact callback: tile, shattered?, mega?, impact point. */
    var onImpact: ((tile: Int, shattered: Boolean, mega: Boolean, x: Float, y: Float) -> Unit)? = null

    // ---------------- projectiles ----------------
    val maxShots = 24
    val shotActive = BooleanArray(maxShots)
    val shotFromX = FloatArray(maxShots); val shotFromY = FloatArray(maxShots)
    val shotToX = FloatArray(maxShots); val shotToY = FloatArray(maxShots)
    val shotT = FloatArray(maxShots); val shotDur = FloatArray(maxShots)
    val shotTile = IntArray(maxShots)
    /** Laser beams stay visible a moment after their (instant) hit. */
    val beamLeft = FloatArray(maxShots)

    // ---------------- shards ----------------
    val maxShards = 420
    var shardCount = 0; private set
    val shardTile = IntArray(maxShards)
    /** Triangle corners relative to the tile centre (x0, y0, x1, y1, x2, y2). */
    val shardTri = FloatArray(maxShards * 6)
    val shardX = FloatArray(maxShards); val shardY = FloatArray(maxShards)
    val shardVx = FloatArray(maxShards); val shardVy = FloatArray(maxShards)
    val shardRot = FloatArray(maxShards); val shardVr = FloatArray(maxShards)
    val shardAge = FloatArray(maxShards)
    /** v10.6: shards no longer vanish; they settle into a pile on the floor. Resting = stopped on the pile. */
    val shardRest = BooleanArray(maxShards)
    /** 1 = fully visible; < 1 = fading out (oldest resting shards over [restCap]). */
    val shardAlpha = FloatArray(maxShards) { 1f }
    private val shardPileAdd = FloatArray(maxShards)
    /** Most shards allowed to rest in the pile; older ones fade away. */
    val restCap = 120
    /** Floor pile height per column bin (px above the bottom edge). */
    private val binW = 14f * density
    val pileH = FloatArray(max(1, (width / binW).toInt() + 1))
    private val floorPad = 6f * density
    private val pileMax = 44f * density

    /** v10.6 hit-stop: seconds the simulation stays frozen after a big hit (drawing keeps going). */
    var hitStop = 0f; private set

    fun tileAt(x: Float, y: Float): Int {
        if (x < 0f || y < 0f || x >= width || y >= height) return -1
        return min(rows - 1, (y / tileH).toInt()) * cols + min(cols - 1, (x / tileW).toInt())
    }
    fun tileLeft(i: Int) = (i % cols) * tileW
    fun tileTop(i: Int) = (i / cols) * tileH

    /** Fire at screen ([x], [y]) from ([fromX], [fromY]). False if there's nothing there to smash. */
    fun tap(x: Float, y: Float, fromX: Float, fromY: Float, attack: Attack): Boolean {
        if (state != State.PLAYING) return false
        val tile = tileAt(x, y)
        if (tile < 0 || hp[tile] <= 0) return false
        val s = (0 until maxShots).firstOrNull { !shotActive[it] && beamLeft[it] <= 0f } ?: return false
        shotActive[s] = true; shotTile[s] = tile
        shotFromX[s] = fromX; shotFromY[s] = fromY; shotToX[s] = x; shotToY[s] = y
        shotT[s] = 0f
        // Thrown things take a little longer to go further.
        shotDur[s] = if (attack.travelSecs == 0f) 0f else attack.travelSecs * (0.75f + 0.5f * min(1f, hypot(x - fromX, y - fromY) / height))
        if (shotDur[s] == 0f) { beamLeft[s] = 0.16f; impact(s) }
        return true
    }

    /** Where shot [s] is now (with an arc for thrown attacks). */
    fun shotPos(s: Int, arc: Boolean, out: FloatArray) {
        val k = if (shotDur[s] <= 0f) 1f else (shotT[s] / shotDur[s]).coerceIn(0f, 1f)
        out[0] = shotFromX[s] + (shotToX[s] - shotFromX[s]) * k
        out[1] = shotFromY[s] + (shotToY[s] - shotFromY[s]) * k - (if (arc) 0.22f * abs(shotToY[s] - shotFromY[s]) * 4f * k * (1f - k) else 0f)
    }

    private fun impact(s: Int) {
        shotActive[s] = false
        val tile = shotTile[s]
        val x = shotToX[s]; val y = shotToY[s]
        if (hp[tile] <= 0) return
        val mega = megaReady
        megaReady = false
        comboT = 0.9f; combo++
        if (combo % MEGA_EVERY == 0) megaReady = true
        val mult = 1f + min(2f, combo / 5f)
        if (mega) {
            val r0 = tile / cols; val c0 = tile % cols
            for (r in r0 - 1..r0 + 1) for (c in c0 - 1..c0 + 1) {
                if (r !in 0 until rows || c !in 0 until cols) continue
                val t = r * cols + c
                if (hp[t] > 0) { setHit(t, x, y); shatter(t, x, y, 1.6f); score += (25 * mult).toInt() }
            }
        } else if (hp[tile] == 2) {
            hp[tile] = 1; setHit(tile, x, y); score += (10 * mult).toInt()
        } else {
            shatter(tile, x, y, 1f); score += (25 * mult).toInt()
        }
        // v10.6 hit-stop: a short freeze sells the weight of the hit (~50 ms per break, ~90 ms for a mega).
        if (mega) hitStop = max(hitStop, HIT_STOP_MEGA) else if (hp[tile] == 0) hitStop = max(hitStop, HIT_STOP_BREAK)
        onImpact?.invoke(tile, hp[tile] == 0, mega, x, y)
        if (smashed == tiles) finish(allSmashed = true)
    }

    private fun setHit(t: Int, x: Float, y: Float) {
        hitU[t] = ((x - tileLeft(t)) / tileW).coerceIn(0.1f, 0.9f)
        hitV[t] = ((y - tileTop(t)) / tileH).coerceIn(0.1f, 0.9f)
        crackSeed[t] = rnd.nextInt(1 shl 20)
    }

    /** Break tile [t] into 8 triangles fanned around the crack point, flying away from ([x], [y]). */
    private fun shatter(t: Int, x: Float, y: Float, power: Float) {
        hp[t] = 0; brokenT[t] = 0f; smashed++
        val hw = tileW / 2f; val hh = tileH / 2f
        val cx = (hitU[t] - 0.5f) * tileW; val cy = (hitV[t] - 0.5f) * tileH
        // Boundary points clockwise: corners and edge midpoints (slightly jittered).
        val bx = floatArrayOf(-hw, 0f, hw, hw, hw, 0f, -hw, -hw)
        val by = floatArrayOf(-hh, -hh, -hh, 0f, hh, hh, hh, 0f)
        for (k in 1 until 8 step 2) { bx[k] += (rnd.nextFloat() - 0.5f) * tileW * 0.3f; by[k] += (rnd.nextFloat() - 0.5f) * tileH * 0.3f }
        val tcx = tileLeft(t) + hw; val tcy = tileTop(t) + hh
        for (k in 0 until 8) {
            val n = (k + 1) % 8
            val i = if (shardCount < maxShards) shardCount++ else oldestShard()
            shardTile[i] = t
            // Store the triangle around its own centroid so it spins about its middle.
            val gx = (cx + bx[k] + bx[n]) / 3f; val gy = (cy + by[k] + by[n]) / 3f
            val o = i * 6
            shardTri[o] = cx - gx; shardTri[o + 1] = cy - gy
            shardTri[o + 2] = bx[k] - gx; shardTri[o + 3] = by[k] - gy
            shardTri[o + 4] = bx[n] - gx; shardTri[o + 5] = by[n] - gy
            shardX[i] = tcx + gx; shardY[i] = tcy + gy
            var dx = shardX[i] - x; var dy = shardY[i] - y
            val len = max(1f, hypot(dx, dy)); dx /= len; dy /= len
            val sp = (260f + rnd.nextFloat() * 520f) * density * power
            shardVx[i] = dx * sp + (rnd.nextFloat() - 0.5f) * 160f * density
            shardVy[i] = dy * sp - (200f + rnd.nextFloat() * 380f) * density * power
            shardRot[i] = 0f; shardVr[i] = (rnd.nextFloat() - 0.5f) * 900f
            shardAge[i] = 0f; shardRest[i] = false; shardAlpha[i] = 1f; shardPileAdd[i] = 0f
        }
    }

    private fun oldestShard(): Int {
        var best = 0; for (i in 1 until shardCount) if (shardAge[i] > shardAge[best]) best = i
        unpile(best); return best
    }

    private fun bin(x: Float) = (x / binW).toInt().coerceIn(0, pileH.size - 1)
    /** Floor height under [x]: the bottom edge minus the pile already there. */
    fun floorAt(x: Float) = height - floorPad - pileH[bin(x)]
    private fun unpile(i: Int) { if (shardPileAdd[i] > 0f) { val b = bin(shardX[i]); pileH[b] = max(0f, pileH[b] - shardPileAdd[i]); shardPileAdd[i] = 0f } }

    private fun removeShard(i: Int) {
        unpile(i)
        val l = --shardCount
        if (i != l) {
            shardTile[i] = shardTile[l]; System.arraycopy(shardTri, l * 6, shardTri, i * 6, 6)
            shardX[i] = shardX[l]; shardY[i] = shardY[l]; shardVx[i] = shardVx[l]; shardVy[i] = shardVy[l]
            shardRot[i] = shardRot[l]; shardVr[i] = shardVr[l]; shardAge[i] = shardAge[l]
            shardRest[i] = shardRest[l]; shardAlpha[i] = shardAlpha[l]; shardPileAdd[i] = shardPileAdd[l]
        }
    }

    private fun clearShards() { shardCount = 0; pileH.fill(0f) }

    private fun finish(allSmashed: Boolean) {
        if (state != State.PLAYING) return
        if (allSmashed) score += (timeLeft * 20f).toInt()
        state = State.OVER
        for (s in 0 until maxShots) shotActive[s] = false
    }

    /** Put the screen back together (after the score card); [State.DONE] when finished. */
    fun repair() { if (state == State.OVER) { state = State.REPAIR; repairK = 0f } }

    /** Stop now (X / back): no score card, straight to the screen repairing itself. */
    fun quit() {
        if (state == State.PLAYING) { state = State.OVER; for (s in 0 until maxShots) shotActive[s] = false }
        repair()
    }

    /** Play again on the same picture. */
    fun reset() {
        for (i in 0 until tiles) { hp[i] = 2; brokenT[i] = -1f }
        for (s in 0 until maxShots) { shotActive[s] = false; beamLeft[s] = 0f }
        clearShards(); hitStop = 0f; score = 0; combo = 0; comboT = 0f; megaReady = false; smashed = 0
        timeLeft = durationSecs; repairK = 0f; state = State.PLAYING
    }

    fun step(frameDt: Float) {
        var dt = frameDt
        if (hitStop > 0f) {
            // Frozen: timer, shots and shards all hold still; the view keeps drawing (shake, bursts).
            val used = min(hitStop, dt); hitStop -= used; dt -= used
            if (state == State.PLAYING && dt <= 0f) return
        }
        when (state) {
            State.PLAYING -> {
                timeLeft = max(0f, timeLeft - dt)
                if (comboT > 0f) { comboT -= dt; if (comboT <= 0f) { combo = 0; megaReady = false } }
                for (s in 0 until maxShots) {
                    if (beamLeft[s] > 0f) beamLeft[s] -= dt
                    if (!shotActive[s]) continue
                    shotT[s] += dt
                    if (shotT[s] >= shotDur[s]) impact(s)
                }
                if (timeLeft <= 0f) finish(allSmashed = false)
            }
            State.REPAIR -> { repairK = min(1f, repairK + dt / 0.9f); if (repairK >= 1f) { state = State.DONE; clearShards() } }
            else -> Unit
        }
        for (i in 0 until tiles) if (brokenT[i] >= 0f) brokenT[i] += dt
        // v10.6 shards: gravity, spin, low bounces off the walls and floor, then they settle into a pile and stay.
        var resting = 0
        var i = 0
        while (i < shardCount) {
            shardAge[i] += dt
            if (shardAlpha[i] < 1f) {
                shardAlpha[i] -= dt / 0.4f
                if (shardAlpha[i] <= 0f) { removeShard(i); continue }
            }
            if (shardRest[i]) { if (shardAlpha[i] >= 1f) resting++; i++; continue }
            if (shardY[i] > height + tileH) { removeShard(i); continue }
            shardVy[i] += 2600f * density * dt
            shardX[i] += shardVx[i] * dt; shardY[i] += shardVy[i] * dt
            shardRot[i] += shardVr[i] * dt
            if (shardX[i] < 0f && shardVx[i] < 0f) { shardX[i] = 0f; shardVx[i] = -shardVx[i] * 0.4f }
            if (shardX[i] > width && shardVx[i] > 0f) { shardX[i] = width; shardVx[i] = -shardVx[i] * 0.4f }
            val floor = floorAt(shardX[i])
            if (shardY[i] > floor && shardVy[i] > 0f) {
                shardY[i] = floor
                if (shardVy[i] < 220f * density) {
                    // Settled: stop and add a little to the pile under it.
                    shardRest[i] = true; shardVx[i] = 0f; shardVy[i] = 0f; shardVr[i] = 0f
                    val b = bin(shardX[i]); val add = min(3f * density, pileMax - pileH[b]).coerceAtLeast(0f)
                    pileH[b] += add; shardPileAdd[i] = add
                    resting++
                } else {
                    shardVy[i] = -shardVy[i] * 0.28f; shardVx[i] *= 0.55f; shardVr[i] *= 0.5f
                }
            }
            i++
        }
        // Too many resting: fade the oldest ones still fully visible.
        var over = resting - restCap
        while (over > 0) {
            var best = -1
            for (k in 0 until shardCount) if (shardRest[k] && shardAlpha[k] >= 1f && (best < 0 || shardAge[k] > shardAge[best])) best = k
            if (best < 0) break
            shardAlpha[best] = 0.999f; over--
        }
    }

    /** Unit circle point for crack rays (shared by the drawing code and tests). */
    fun rayDir(seed: Int, k: Int, out: FloatArray) {
        val a = (k * 2.0 * Math.PI / CRACK_RAYS + ((seed shr (k * 3)) and 7) * 0.09).toFloat()
        out[0] = cos(a); out[1] = sin(a)
    }

    companion object {
        const val MEGA_EVERY = 8
        const val CRACK_RAYS = 6
        const val HIT_STOP_BREAK = 0.05f
        const val HIT_STOP_MEGA = 0.09f
    }
}
