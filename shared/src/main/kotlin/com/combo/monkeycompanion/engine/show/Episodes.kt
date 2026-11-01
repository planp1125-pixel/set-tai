package com.combo.monkeycompanion.engine.show

/**
 * v10.21 the Billa & Chittu cartoon shows. v10.22: built by [Drama] from mix-and-match acts (2,400 combinations),
 * starting from wherever the real characters are. [byIndex] is the same show from a default line-up (previews/tests).
 */
object Episodes {
    /** A default line-up: Billa on the floor left of centre, Chittu on the right. */
    val defaultCat = ActorStart.floor(0.3f, 1f)
    val defaultRat = ActorStart.floor(0.75f, -1f)

    fun byIndex(i: Int): Episode = Drama.build(i, defaultCat, defaultRat)
    fun build(i: Int, cat: ActorStart, rat: ActorStart): Episode = Drama.build(i, cat, rat)
}
