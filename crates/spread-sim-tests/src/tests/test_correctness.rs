use crate::scenarios;

#[test]
fn test_we_love_np_7() {
    scenarios::WE_LOVE_NP.test_case().with_padding(7).launch()
}

#[test]
fn test_we_love_np_10() {
    scenarios::WE_LOVE_NP.test_case().with_padding(10).launch()
}

#[test]
fn test_we_love_np_15() {
    scenarios::WE_LOVE_NP.test_case().with_padding(15).launch()
}
