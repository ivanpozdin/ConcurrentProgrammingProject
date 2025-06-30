use std::sync::Arc;

use spread_sim_core::{
    InsufficientPaddingError,
    model::{output::Output, scenario::Scenario},
    validator::Validator,
};

/// Launches your concurrent implementation. 🚀
///
/// You must not modify the signature of this function as our tests rely on it.
///
/// Note that the [`Validator`] is wrapped in an [`Arc`] and can be cloned and passed
/// around freely (it is [`Sync`] and [`Send`]).
///
/// - *scenario*: The [`Scenario`] to simulate.
/// - *padding*: The padding to use for the simulation.
/// - *validator*: The [`Validator`] to call (for testing).
/// - *starship*: Indicates whether the implementation of assignment 2 should be used.
pub fn launch(
    scenario: Scenario,
    padding: usize,
    validator: Arc<dyn Validator>,
    starship: bool,
) -> Result<Output, InsufficientPaddingError> {
    // The next line is here simply to suppress unused variable warning. You should
    // remove it and actually use the provided arguments. ;)
    let _ = (scenario, padding, validator, starship);
    if starship {
        // Launch your starship here.
        //
        // Note that you may ignore the padding and validator parameters in this case.
        panic!("Starship has not been implemented.")
    } else {
        // Begin implementing your concurrent implementation here.
        todo!("Rocket has not been implemented.");
    }
}
