package com.combo.monkeycompanion.rig

/** v10.9 the inner core of a burst. */
enum class FwCore { NONE, CRACKLE, GLITTER, STROBE }
/** v10.9 the outer extra layer of a burst. */
enum class FwOuter { NONE, WILLOW, CROSSETTE, COMET, HORSETAIL }

/**
 * v10.9 one firework "recipe": a named combination of the main [shape], how many concentric [rings] (layers at different
 * radii and colours, the main one included), an inner [core], an [outer] layer, a fixed [palette] (null = rolled each
 * time), [colourChange] (stars change colour mid-flight) and [multiBreak] (some stars re-burst).
 */
data class FwRecipe(
    val name: String, val shape: FwShape, val rings: Int, val core: FwCore = FwCore.NONE, val outer: FwOuter = FwOuter.NONE,
    val palette: FwPalette? = null, val colourChange: Boolean = false, val multiBreak: Boolean = false,
)

/** v10.9 the recipe book: 50+ distinct bursts. Layered multi-ring is the norm (every recipe has at least 2 layers). */
object FireworkRecipes {
    private fun r(name: String, shape: FwShape, rings: Int, core: FwCore = FwCore.NONE, outer: FwOuter = FwOuter.NONE,
                  pal: FwPalette? = null, change: Boolean = false, multi: Boolean = false) = FwRecipe(name, shape, rings, core, outer, pal, change, multi)

    val ALL: List<FwRecipe> = listOf(
        // peonies and chrysanthemums
        r("Classic peony", FwShape.PEONY, 2),
        r("Peony with crackling core", FwShape.PEONY, 3, FwCore.CRACKLE),
        r("Peony willow", FwShape.PEONY, 2, outer = FwOuter.WILLOW),
        r("Peony colour change", FwShape.PEONY, 3, change = true),
        r("Peony crossette", FwShape.PEONY, 2, outer = FwOuter.CROSSETTE),
        r("Peony strobe heart", FwShape.PEONY, 3, FwCore.STROBE),
        r("Gold peony", FwShape.PEONY, 2, FwCore.GLITTER, pal = FwPalette.GOLD),
        r("Chrysanthemum", FwShape.CHRYSANTHEMUM, 2),
        r("Chrysanthemum crackle", FwShape.CHRYSANTHEMUM, 3, FwCore.CRACKLE),
        r("Chrysanthemum comets", FwShape.CHRYSANTHEMUM, 2, outer = FwOuter.COMET),
        r("Chrysanthemum glitter core", FwShape.CHRYSANTHEMUM, 4, FwCore.GLITTER),
        r("Silver chrysanthemum", FwShape.CHRYSANTHEMUM, 3, FwCore.STROBE, pal = FwPalette.SILVER),
        r("Dahlia", FwShape.DAHLIA, 2),
        r("Dahlia willow", FwShape.DAHLIA, 3, outer = FwOuter.WILLOW),
        r("Dahlia colour change", FwShape.DAHLIA, 2, FwCore.CRACKLE, change = true),
        // rings
        r("Ring", FwShape.RING, 2),
        r("Ring with peony heart", FwShape.RING, 3, FwCore.GLITTER),
        r("Ring strobe", FwShape.RING, 2, FwCore.STROBE),
        r("Double ring", FwShape.DOUBLE_RING, 2),
        r("Double ring crackle", FwShape.DOUBLE_RING, 3, FwCore.CRACKLE),
        r("Double ring crossette", FwShape.DOUBLE_RING, 2, outer = FwOuter.CROSSETTE),
        r("Triple ring", FwShape.TRIPLE_RING, 3),
        r("Triple ring willow", FwShape.TRIPLE_RING, 4, outer = FwOuter.WILLOW),
        r("Triple ring rainbow", FwShape.TRIPLE_RING, 3, pal = FwPalette.RAINBOW),
        // willows, palms, horsetails, kamuro
        r("Gold willow", FwShape.WILLOW, 2, pal = FwPalette.GOLD),
        r("Willow crackle", FwShape.WILLOW, 3, FwCore.CRACKLE),
        r("Willow with strobes", FwShape.WILLOW, 2, FwCore.STROBE),
        r("Kamuro", FwShape.KAMURO, 2, FwCore.GLITTER),
        r("Kamuro crossette", FwShape.KAMURO, 3, outer = FwOuter.CROSSETTE),
        r("Palm", FwShape.PALM, 2),
        r("Palm with comets", FwShape.PALM, 3, outer = FwOuter.COMET),
        r("Palm crackle", FwShape.PALM, 2, FwCore.CRACKLE),
        r("Horsetail", FwShape.HORSETAIL, 2, outer = FwOuter.HORSETAIL),
        r("Horsetail glitter", FwShape.HORSETAIL, 3, FwCore.GLITTER),
        // crossettes, comets, crackle, strobe, glitter
        r("Crossette", FwShape.CROSSETTE, 2, outer = FwOuter.CROSSETTE),
        r("Crossette strobe", FwShape.CROSSETTE, 3, FwCore.STROBE),
        r("Comet burst", FwShape.COMET, 2, outer = FwOuter.COMET),
        r("Comet willow", FwShape.COMET, 3, outer = FwOuter.WILLOW),
        r("Crackling flower", FwShape.CRACKLE, 2, FwCore.CRACKLE),
        r("Crackle ring", FwShape.CRACKLE, 3, outer = FwOuter.HORSETAIL),
        r("Strobe sphere", FwShape.STROBE, 2, FwCore.STROBE),
        r("Strobe willow", FwShape.STROBE, 3, outer = FwOuter.WILLOW),
        r("Glitter", FwShape.GLITTER, 2, FwCore.GLITTER),
        r("Glitter rings", FwShape.GLITTER, 3),
        // pictures
        r("Heart", FwShape.HEART, 2),
        r("Heart in a ring", FwShape.HEART, 3, FwCore.GLITTER),
        r("Star", FwShape.STAR, 2),
        r("Star with crackle", FwShape.STAR, 3, FwCore.CRACKLE),
        r("Smiley", FwShape.SMILEY, 2, pal = FwPalette.GOLD),
        r("Smiley strobe", FwShape.SMILEY, 2, FwCore.STROBE),
        // multi-break, spirals
        r("Multi-break", FwShape.MULTI_STAGE, 2, multi = true),
        r("Multi-break crackle", FwShape.MULTI_STAGE, 3, FwCore.CRACKLE, multi = true),
        r("Peony multi-break", FwShape.PEONY, 3, multi = true),
        r("Spiral chakra", FwShape.SPIRAL, 2),
        r("Spiral with glitter", FwShape.SPIRAL, 3, FwCore.GLITTER),
        r("Spiral colour change", FwShape.SPIRAL, 2, change = true),
        r("Grand chrysanthemum", FwShape.CHRYSANTHEMUM, 4, FwCore.CRACKLE, FwOuter.WILLOW),
        r("Rainbow peony", FwShape.PEONY, 4, FwCore.STROBE, pal = FwPalette.RAINBOW),
    )
}
