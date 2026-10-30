package com.combo.monkeycompanion.brain

/** What the monkey is doing right now. Drives the animation and the touch reactions. */
enum class MonkeyState {
    IDLE, WALK, DANCE, DRAGGED, LANDING, GIGGLE, HAPPY_PAT, STARTLED, ANGRY, PLAYFUL,
    /** Waiting for the AI: scratching its head. */
    THINKING,
    /** Speaking an AI reply: idle with explaining gestures. */
    TALKING,
    /** Let go / dropped mid-screen: falling to the ground. */
    FALLING,
    /** Screen off: curled up asleep at the bottom with Zzz. */
    SLEEP,
    /** Waking up after unlock: yawn, stretch, rub eyes, jump, shake, wave. */
    WAKE,
    /** v3: wake / un-hide entrance: head peeks out of an edge, grips it, pulls itself up. */
    ENTRANCE,
    /** Head poking out from behind something, looking around. */
    PEEK,
    /** Fully hidden (behind the screen edge or a widget). */
    HIDDEN,
    /** Sitting on a ledge (top of a widget). */
    SIT,
    /** Jumping / leaping. */
    JUMP,
    /** Dashing off to hide behind the screen edge. */
    DASH,
    SAD,
    WAVE,
    /** v3.2: a comedy bit ([Gag]): sneeze, banana slip, juggling, air guitar, selfie... */
    GAG,
    /** v3.2: leaving for good (user quit): one of the [Exit] routines. Held until the host removes Settai. */
    EXIT,
    /** v3.2: using a [Superpower] (flying around the screen). Held until the host ends the routine. */
    POWER;
}

/** v3.2 comedy bits, each with its length in seconds. */
/** Ways to leave the screen when the user quits (picked at random). No weapons, nothing dark: pure slapstick. */
enum class Exit(val seconds: Float) {
    /** Movie-hero faint: back of the hand to the forehead, wobble, swoon, fade out. */
    FAINT(3.8f),
    /** Lights a banana rocket and blasts off the top of the screen with a smoke trail. */
    ROCKET(3.6f),
    /** Waves "bye bye machan" and vanishes in a puff of smoke. */
    POOF(2.9f)
}

enum class Gag(val seconds: Float, val tag: String) {
    SNEEZE(2.6f, "sneeze"),
    SLIP(3.0f, "slip"),
    BINOCULARS(3.6f, "binoculars"),
    FAKE_SLEEP(4.8f, "fakesleep"),
    JUGGLE(4.4f, "juggle"),
    TAIL_HIDE(3.8f, "tailhide"),
    CHASE_FLY(4.6f, "chasefly"),
    AIR_GUITAR(4.2f, "airguitar"),
    SELFIE(3.4f, "selfie");

    /** Gags that need flat ground under the feet. */
    val needsFloor get() = this == SLIP || this == AIR_GUITAR || this == FAKE_SLEEP
}

/** Which screen edge the monkey is on. FREE = parked mid-screen (roaming off). */
enum class Edge { BOTTOM, TOP, LEFT, RIGHT, FREE;
    val isWall get() = this == LEFT || this == RIGHT
}

/** Body regions the rig can recognise under a finger. */
enum class TouchZone { NONE, HEAD, NECK, BELLY, PRIVATE, TAIL, HAND, FOOT }
