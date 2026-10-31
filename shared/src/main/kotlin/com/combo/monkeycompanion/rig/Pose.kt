package com.combo.monkeycompanion.rig

import kotlin.reflect.KProperty

/**
 * Every animatable parameter of the monkey rig. Values live in one FloatArray so poses
 * can be copied and blended cheaply every frame.
 *
 * Angles are degrees. Arm "raise" / leg "swing" are measured OUTWARD from hanging straight
 * down, for each side, so the animation code never has to think about mirroring.
 * Units are rig units (the monkey is ~165 units tall), y grows downward.
 */
class Pose {
    val v = FloatArray(MAX)
    private var count = 0

    private inner class Slot(val i: Int) {
        operator fun getValue(p: Pose, prop: KProperty<*>): Float = v[i]
        operator fun setValue(p: Pose, prop: KProperty<*>, x: Float) { v[i] = x }
    }

    private val defaults = FloatArray(MAX)
    private fun slot(def: Float): Slot {
        val i = count++
        v[i] = def; defaults[i] = def
        return Slot(i)
    }

    // Body / root
    var bodyX by slot(0f)
    var bodyY by slot(0f)          // negative = up (jumps, bounces)
    var squashX by slot(1f)
    var squashY by slot(1f)
    var bodyRot by slot(0f)
    // Head
    var headRot by slot(0f)
    var headY by slot(0f)
    // Arms
    var armLRaise by slot(14f)
    var armLElbow by slot(-8f)
    var armRRaise by slot(14f)
    var armRElbow by slot(-8f)
    // Legs
    var legLSwing by slot(0f)
    var legLLift by slot(0f)
    var legRSwing by slot(0f)
    var legRLift by slot(0f)
    // Tail
    var tailRot by slot(0f)        // positive = raised
    var tailSway by slot(0f)       // -1..1 extra curl wave
    // Face
    var eyeOpen by slot(1f)        // 0 closed, 1 normal, >1 wide
    var eyesHappy by slot(0f)      // 1 = ^ ^ closed smiling eyes
    var lookX by slot(0f)
    var lookY by slot(0f)
    var browAngle by slot(0f)      // + angry (inner ends down), - worried
    var browY by slot(0f)
    var mouthOpen by slot(0f)
    var mouthSmile by slot(0.6f)   // -1 frown .. 1 big smile
    var blush by slot(0f)
    var anger by slot(0f)          // red face tint + anger vein
    // Effects
    var hearts by slot(0f)
    var notes by slot(0f)
    var sweat by slot(0f)          // reserved for v2 (heat)
    var mouthCloth by slot(0f)     // reserved for v2 (silent / DND)
    // v2.1: edges & sleep
    var backView by slot(0f)       // >0.5 = seen from behind (climbing a wall)
    var zzz by slot(0f)            // floating sleep Zzz
    var shadow by slot(1f)         // ground shadow (0 when hanging / climbing / falling)
    var earWiggle by slot(0f)      // ear twitch / jiggle (-1..1)
    var armStretch by slot(1f)     // arm length multiplier (long arms while hanging)
    var hangSwing by slot(0f)      // degrees: whole body swings around the hands (hanging)
    // v3.1 props for phone powers
    var shades by slot(0f)         // sunglasses (brightness up)
    var earPaws by slot(0f)        // paws over the ears (volume up)
    // v3.2 gags
    var wink by slot(0f)           // >0.5: left eye shut, right eye uses eyeOpen (one-eye-open fake sleep)
    var propT by slot(0f)          // seconds into the current gag (drives prop motion)
    var bananas by slot(0f)        // juggling bananas
    var binoculars by slot(0f)
    var phone by slot(0f)          // selfie phone in the right hand
    var flash by slot(0f)          // camera flash burst
    var guitar by slot(0f)         // air guitar (it's imaginary... mostly)
    var fly by slot(0f)            // a pesky fly
    var flyX by slot(0f)           // fly position, rig units from the feet anchor
    var flyY by slot(-150f)
    var peel by slot(0f)           // banana peel on the ground
    var peelX by slot(24f)
    var sneeze by slot(0f)         // ACHOO spray
    // v4.2
    var earPerk by slot(0f)        // hold-to-talk: ears up and bigger, little sound arcs
    var tailFront by slot(0f)      // >0.5: tail drawn in front of the face (hiding badly)
    // v3.2 exits
    var fade by slot(1f)           // whole-monkey opacity
    var rocket by slot(0f)         // banana rocket behind the monkey
    var flame by slot(0f)          // rocket exhaust 0..1
    var smoke by slot(0f)          // smoke trail from the ground up to the rocket
    var puff by slot(0f)           // 0..1 progress of the vanishing puff of smoke
    // v3.2 superpowers
    var jets by slot(0f)           // foot flame jets 0..1
    var palmGlow by slot(0f)       // glowing palms (ring blasts)
    var cape by slot(0f)           // red cape on
    var capeWind by slot(0f)       // 0..1 airflow over the cape (flight speed)
    var soot by slot(0f)           // sooty face after a crash
    var badge by slot(0f)          // banana badge on the chest
    // v4 agent props
    var goggles by slot(0f)        // scanner goggles (reading the screen)
    var wheel by slot(0f)          // steering wheel (screen agent driving)
    var magnifier by slot(0f)      // detective magnifier (notifications)
    var notebook by slot(0f)       // notebook + pencil (memory)
    var stethoscope by slot(0f)    // phone doctor
    var globe by slot(0f)          // web lookups / weather
    var micGlow by slot(0f)        // listening (push-to-talk)
    // v4 show-off powers
    var size by slot(1f)           // whole-body scale (Kovam grow / shrink), from the feet
    var staff by slot(0f)          // lightning staff in the right hand
    var staffGlow by slot(0f)      // crackle on the staff (charge)
    var frizz by slot(0f)          // fur standing on end (zapped)
    var ninja by slot(0f)          // ninja headband + mask
    var frost by slot(0f)          // icy blue tint + icicles
    var steam by slot(0f)          // angry steam from the ears
    var hat by slot(0f)            // top hat in the left hand
    var rabbit by slot(0f)         // 0..1 rabbit popping out of the hat
    var wand by slot(0f)           // magic wand in the right hand
    // v4.1 characters + wardrobe
    var hatOff by slot(0f)         // >0.5: hat is off the head (stolen by the crow / blown away)
    var stacheOff by slot(0f)      // 0..1 moustache flying off (sneeze)
    var chicken by slot(0f)        // rubber chicken in the right hand
    var squeak by slot(0f)         // 0..1 chicken squeezed ("SQUEAK!")
    var umbrella by slot(0f)       // open umbrella overhead (floating down)
    var skate by slot(0f)          // standing on a skateboard
    var beltPull by slot(0f)       // 0..1 pulling something out of the utility belt
    var beltItem by slot(0f)       // which absurd gadget (index)
    var eyePop by slot(0f)         // 0..1 mask eyes popping out (surprise)
    var bananaPhone by slot(0f)    // banana phone at the ear
    var visor by slot(0f)          // 0 visor down .. 1 flipped up (armour helmet)
    var shield by slot(0f)         // 0..1 shield bubble
    var lowPower by slot(0f)       // 0..1 armour running out of charge (dim core, droop)
    var laser by slot(0f)          // 0..1 heat-vision eyes glowing
    var breath by slot(0f)         // 0..1 frost breath from the mouth
    var lift by slot(0f)           // 0..1 holding something huge overhead
    var hud by slot(0f)            // 0..1 scanner HUD around the helmet
    var blanket by slot(0f)        // cape used as a blanket (floating sleep)
    var clones by slot(0f)         // 0..1 shadow clones / after-images (drawn by the host)
    // v10.8 Rocket Girl + festive accents (visual only; set after the pose blend)
    var rbSputter by slot(0f)      // 0..1 rocket coughing / sputtering
    var rbSpin by slot(0f)         // degrees the rocket rolls about its own axis (stripes scroll)
    var sparkler by slot(0f)       // 0..1 phuljhadi sparkler in the right hand
    var festive by slot(0f)        // 0..1 Diwali-season diya / sparkle accents
    var glint by slot(0f)          // 0..1 appeal: extra eye catch-light twinkle (Smooth animation only)
    // v10.9 Rocket Girl
    var rbThrust by slot(0f)       // 0..1 the rocket's tail flame burst (swipe launch / boost)
    var rgReach by slot(0f)        // 0..1 her right hand reaches down to light the bottle's fuse
    // v10.14 Hollow Man + Surfing Girl (visual only; never motion slots)
    var hmMoon by slot(0f)         // 0..1 moonwalk: the body faces AWAY from the way it glides
    var hmHatTip by slot(0f)       // 0..1 hat lifted off the (invisible) head by the right glove
    var spinX by slot(1f)          // cos of a spin about the vertical axis (1 = front, -1 = back), x scale
    var flipS by slot(0f)          // sin / cos of an aerial flip (blend-safe: no 360 -> 0 snap)
    var flipC by slot(1f)
    var surf by slot(0f)           // 0..1 standing on the surfboard over a wave
    var surfSpray by slot(0f)      // 0..1 extra splash (big wave ride, landing, wipeout)
    // v10.16 Kutti Satan, the little devil (visual only; never motion slots)
    var dvPortal by slot(0f)       // 0..1 the red swirl portal he pops out of (reappear half of the vanish)
    var dvSmoke by slot(0f)        // 0..1 the red smoke puff + sparkles he vanishes into
    var dvSpell by slot(0f)        // 0..1 red spell sparks crackling between his fingers
    var dvAura by slot(0f)         // 0..1 red levitation sparkles around him (float tricks)
    var dvClone by slot(0f)        // 0..1 shadow clones beside him

    fun reset() { System.arraycopy(defaults, 0, v, 0, count) }
    fun set(o: Pose) { System.arraycopy(o.v, 0, v, 0, count) }

    /** Move a fraction [a] (0..1) of the way toward [o]. */
    fun blendToward(o: Pose, a: Float) {
        for (i in 0 until count) v[i] += (o.v[i] - v[i]) * a
    }

    companion object { private const val MAX = 128 }
}
