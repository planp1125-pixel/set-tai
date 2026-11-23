// R2: build with the trained model when mind/settai-mind/model/intent.bin exists, otherwise rules only.
fn main() {
    println!("cargo::rustc-check-cfg=cfg(has_model)");
    println!("cargo::rerun-if-changed=model/intent.bin");
    println!("cargo::rerun-if-changed=model");
    if std::path::Path::new("model/intent.bin").exists() {
        println!("cargo::rustc-cfg=has_model");
    }
}
