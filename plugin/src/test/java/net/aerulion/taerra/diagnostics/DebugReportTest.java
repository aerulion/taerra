package net.aerulion.taerra.diagnostics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DebugReportTest {

  @Test
  @DisplayName("a public URL counts as a subpath only when it has a path after the host")
  void subpath() {
    assertTrue(DebugReport.subpath("https://example.com/map"));
    assertTrue(DebugReport.subpath("HTTP://example.com:8080/a/b"));
    assertFalse(DebugReport.subpath("https://map.example.com"));
    assertFalse(DebugReport.subpath(""));
  }
}
