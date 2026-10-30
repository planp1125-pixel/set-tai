package com.combo.monkeycompanion.engine

import com.combo.monkeycompanion.brain.Edge
import kotlin.math.max
import kotlin.math.min

/** v10 (M1): what a body can stand on, hang from or climb (moved unchanged from MonkeyView's private `SK`). */
enum class SurfaceKind { FLOOR, CEIL, WALL_L, WALL_R }

/**
 * Something to stand on, hang from or climb. [fix] is the box's fixed coordinate (my for floors
 * and ceilings, mx for walls), [a]..[b] the range of the other one. [line] is the surface in
 * screen px (wall x / ceiling y / floor y) and [spanA]..[spanB] where hands and feet may grip.
 * [spot] -1 = screen edge, -2 = parked mid-screen, else a climbing-spot index.
 */
class Surface(val kind: SurfaceKind, val fix: Float, a: Float, b: Float, val line: Float, val spanA: Float, val spanB: Float, val spot: Int = -1) {
    val a = min(a, (a + b) / 2f)
    val b = max(b, (a + b) / 2f)
    val wall get() = kind == SurfaceKind.WALL_L || kind == SurfaceKind.WALL_R
    val climb get() = kind != SurfaceKind.FLOOR
    val edge get() = when (kind) {
        SurfaceKind.FLOOR -> if (spot == -2) Edge.FREE else Edge.BOTTOM
        SurfaceKind.CEIL -> Edge.TOP
        SurfaceKind.WALL_L -> Edge.LEFT
        SurfaceKind.WALL_R -> Edge.RIGHT
    }
    val face get() = if (kind == SurfaceKind.WALL_L) -1f else 1f
    fun same(o: Surface) = o.kind == kind && o.spot == spot
}
