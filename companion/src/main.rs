fn main() {
    if let Err(error) = purple_keyboard_bridge::main_command(std::env::args().skip(1).collect()) {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
