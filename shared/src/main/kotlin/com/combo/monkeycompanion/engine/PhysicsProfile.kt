package com.combo.monkeycompanion.engine

/**
 * v10 (M1): per-character physics numbers. [DEFAULT] holds v9.5's exact values (dp-based, multiplied by density);
 * every character uses it until a later milestone tunes them (cat light and bouncy, Armour heavy...).
 */
data class PhysicsProfile(
    /** Free-fall gravity, dp/s². */
    val gravityDp: Float = 2600f,
    /** Terminal fall speed when floating down (fall gravity < 1, e.g. Mask Man's umbrella), dp/s. */
    val floatTerminalDp: Float = 420f,
    /** A cannonball only bounces if it hits the ground faster than this, dp/s. */
    val bounceMinDp: Float = 300f,

    // Flying hero edge crash (v9.3): sputter at the edge, plunge, crash-landed stars.
    val crashSputterSinkDp: Float = 90f,
    val crashSputterBase: Float = 0.12f,
    val crashSputterFlicker: Float = 0.15f,
    val crashSputterHz: Float = 45f,
    val crashPlungeStartDp: Float = 220f,
    val crashGravityDp: Float = 3100f,
    val crashThrustDecay: Float = 0.92f,
    val crashThrustMin: Float = 0.05f,
    val crashDriftDp: Float = 95f,
    val crashSpinDegS: Float = 320f,
    /** Seconds sitting dazed with stars after the crash. */
    val crashDazedSecs: Float = 1.6f,
) {
    companion object { val DEFAULT = PhysicsProfile() }
}
