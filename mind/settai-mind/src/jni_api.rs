//! The bridge to Kotlin: `com.combo.monkeycompanion.mind.RustMind` (app module) declares these as `external`.
//! Every function catches its own errors and returns an empty string (never a crash into the app).

use jni::objects::{JClass, JIntArray, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;

fn jstr(env: &mut JNIEnv, s: &JString) -> String {
    if s.is_null() {
        return String::new();
    }
    env.get_string(s).map(|j| j.into()).unwrap_or_default()
}

fn out(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s).map(|j| j.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_combo_monkeycompanion_mind_RustMind_nativeVersion<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>) -> jstring {
    out(&mut env, crate::VERSION)
}

/// "intent|mood|act|gesture|power|confidence"
#[no_mangle]
pub extern "system" fn Java_com_combo_monkeycompanion_mind_RustMind_nativeUnderstand<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, text: JString<'l>) -> jstring {
    let t = jstr(&mut env, &text);
    let u = crate::understand(&t);
    out(&mut env, &u.encode())
}

/// "intent|mood|act|gesture|power|confidence\nreply line"
#[no_mangle]
pub extern "system" fn Java_com_combo_monkeycompanion_mind_RustMind_nativeRespond<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    character_id: JString<'l>,
    character_name: JString<'l>,
    you: JString<'l>,
    text: JString<'l>,
    seed: jlong,
    recent: JIntArray<'l>,
) -> jstring {
    let id = jstr(&mut env, &character_id);
    let name = jstr(&mut env, &character_name);
    let you = jstr(&mut env, &you);
    let t = jstr(&mut env, &text);
    let mut rec: Vec<u32> = Vec::new();
    if !recent.is_null() {
        if let Ok(n) = env.get_array_length(&recent) {
            let mut buf = vec![0 as jint; n.max(0) as usize];
            if env.get_int_array_region(&recent, 0, &mut buf).is_ok() {
                rec = buf.into_iter().map(|v| v as u32).collect();
            }
        }
    }
    let (u, line) = crate::respond(&id, &name, &you, &t, seed as u64, &rec);
    out(&mut env, &format!("{}\n{}", u.encode(), line))
}

/// Hash of a line (to tell the brain what was said recently).
#[no_mangle]
pub extern "system" fn Java_com_combo_monkeycompanion_mind_RustMind_nativeLineHash<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, line: JString<'l>) -> jint {
    let l = jstr(&mut env, &line);
    crate::reply::line_hash(&l) as jint
}

/// Average nanoseconds for one understand + reply of [text], over [rounds] (the speed shown in Settings).
#[no_mangle]
pub extern "system" fn Java_com_combo_monkeycompanion_mind_RustMind_nativeBenchNanos<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, text: JString<'l>, rounds: jint) -> jlong {
    let t = jstr(&mut env, &text);
    let n = rounds.max(1) as u64;
    let start = std::time::Instant::now();
    for i in 0..n {
        let _ = crate::respond("bench", "Bench", "you", &t, i, &[]);
    }
    (start.elapsed().as_nanos() as u64 / n) as jlong
}
