//! Try the built-in brain: `cargo run --release -p settai-train --example try -- "enna da panra" "hi"`
//! (no arguments: one message per line from stdin).
use std::io::BufRead;

fn show(t: &str) {
    let u = settai_mind::understand(t);
    let model = settai_mind::model::builtin().map(|m| m.predict(t));
    let (rule, _) = settai_mind::understand::understand_rules(t);
    match model {
        Some((i, p)) => println!("{:<44} {}   model={} {:.2} rules={}", t, u.encode(), i.name(), p, rule.name()),
        None => println!("{:<44} {}   (no model) rules={}", t, u.encode(), rule.name()),
    }
}

fn main() {
    println!("{} / model {}", settai_mind::VERSION, settai_mind::model::version());
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.is_empty() {
        for l in std::io::stdin().lock().lines().map_while(Result::ok) {
            show(&l);
        }
    } else {
        args.iter().for_each(|a| show(a));
    }
}
