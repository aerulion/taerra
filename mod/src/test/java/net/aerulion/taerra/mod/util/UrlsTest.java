package net.aerulion.taerra.mod.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UrlsTest {

  @Test
  @DisplayName("the issue link fills in the debug info, and leaves it out when it is too long")
  void newIssue() {
    assertEquals(
        "https://github.com/aerulion/taerra/issues/new?template=bug-mod.yml"
            + "&debug=TAERRA%20debug%0AMod%3A%20410-9f8e7d6",
        Urls.newIssue("bug-mod.yml", "TAERRA debug\nMod: 410-9f8e7d6"));
    assertEquals(
        "https://github.com/aerulion/taerra/issues/new?template=bug-mod.yml",
        Urls.newIssue("bug-mod.yml", "x".repeat(10_000)));
  }
}
