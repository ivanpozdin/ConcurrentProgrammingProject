# CP Project: Concurrent Pandemic Simulation – Template ☕️ & 🦀

Reference implementation and template for the concurrent programming project 2025.

## Structure

This project is structured as follows:

- `crates/`: Rust crates of the project.
  - `spread-sim/`: Command line interface and main binary.
  - `spread-sim-core/`: Core data structures and algorithms.
  - `spread-sim-rocket/`: Your concurrent implementation goes here.
  - `spread-sim-slug/`: Sequential reference implementation.
  - `spread-sim-tests/`: Public tests and testing infrastructure.
- `src/main/java/com/pseuco/cp25/`: Java source code of the project.
  - `model/`: Data structures for the simulation.
  - `simulation/rocket/`: Your implementation goes here.
  - `simulation/common/`: Simulation functionality you might find useful.
  - `simulation/slug/`: The sequential reference implementation.
  - `validator/`: The validator interface.
  - `Simulation.java`: Implements the `main` method.
- `src/test`: Public tests for the project.
- `scenarios`: Some sample scenarios.

We initialized the `project.toml` according to your language preferences, but in case you want to switch the programming language, it is possible there. If you implement the bonus, also enable the `bonus-implemented` flag there.

## Getting Started

A detailed description on how to install Java and Rust can be found in the
[forum](https://cp25.pseuco.com/t/how-to-project-in-vs-code/273).

We recommend you use a proper *Integrated Development Environment* (IDE) for
the project. A good option is [VS Code](https://code.visualstudio.com/). How to
set up the project in VS Code is documented in
[the same forum post](https://cp25.pseuco.com/t/how-to-project-in-vs-code/273).

Which IDE or editor you use is up to you. However, we only provide help for
VS Code. In case you use some other code editor, do not expect help.

## Running the Project

### Java ☕

We use [Gradle](https://gradle.org/) to build the project.
To build the Javadoc run:

```bash
./gradlew javaDoc
```

Afterwards you find the documentation in `build/docs`.

To build a `simulation.jar`-File for your project run:

```bash
./gradlew jar
```

You find the compiled `.jar`-File in `out`.

To run the *public* tests on your project run:

```bash
./gradlew test
```

### Rust 🦀

This project uses Rust `nightly-2025-05-09` because our testing infrastructure uses unstable features. Note that you should not use any unstable features yourself as they may contain soundness holes, i.e., may render Rust's safety guarantees void. As long as you do not explicitly enable any unstable features yourself, you may argue for the correctness of your code based on Rust stable 1.87. The file `rust-toolchain.toml` takes care of configuring your development environment (e.g., `cargo` should automatically use the correct version).

Remember, Cargo is your friend:

- `cargo build` builds the project.
- `cargo doc` generates API documentation. `cargo doc --open` additionally opens
  the generated documentation in a web browser.
- `cargo test --release -- --test-threads=1` runs the tests. Note that the `--test-threads=1` is there to not interfere with the performance of the simulation, given that `cargo` run test concurrently by default.

## For Convenience: A `justfile` 🎉

We provide a [`justfile`](https://just.systems/man/en/). To install `just` via Cargo, run `cargo install just`. You can then run tests simply with `just test`. Note that our `.gitlab-ci.yml` also uses `just`. A complete list of available commands can be obtained via `just help` or `just list`:

``` md
    build      # Build the project
    doc        # Generate API documentation
    doc-open   # Generate API documentation and open it in your web browser
    help       # Print available recipes
    lang       # Get the detected programming language
    lint       # Run clippy on Rust code
    list       # Print available recipes
    run *FLAGS # Build and run the project
    test       # Run the tests 
```

To build and open the documentation, run `just doc --open`.

## Integrated Development Environment

We recommend you use a proper *Integrated Development Environment* (IDE) for this project. A good open source IDE is [VS Code](https://code.visualstudio.com/). Which IDE or editor you use is up to you. However, we only provide help for VS Code. In case you use something else, do not expect help.

In case you decide to use VS Code, open the `spread-simulator.code-workspace` workspace. After opening the workspace, VS Code should ask you whether you want to install the *recommended extensions*. For maximal convenience, please do so. In particular, the *Rust Analyzer* extension is highly recommended.
