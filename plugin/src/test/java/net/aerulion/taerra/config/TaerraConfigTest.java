package net.aerulion.taerra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaerraConfigTest {

  private static final long GIB = 1L << 30;

  @Test
  @DisplayName("caches take a share of the heap, within their floor and ceiling")
  void cacheEntriesFollowTheHeap() {
    assertEquals(128, TaerraConfig.cacheEntries(2 * GIB, 64, 256L << 10, 1, 50_000));
    assertEquals(2207, TaerraConfig.cacheEntries(2 * GIB, 50, 19L << 10, 256, 131_072));
    assertEquals(256, TaerraConfig.cacheEntries(GIB / 8, 50, 19L << 10, 256, 131_072));
    assertEquals(50_000, TaerraConfig.cacheEntries(Long.MAX_VALUE, 10, 256L << 10, 1, 50_000));
  }

  @Test
  @DisplayName("0 sizes a cache automatically, a set size is held to its limit")
  void configuredCacheSizes() {
    assertEquals(300, TaerraConfig.cacheSize(0, 300, 256, 4000));
    assertEquals(1000, TaerraConfig.cacheSize(1000, 300, 256, 4000));
    assertEquals(4000, TaerraConfig.cacheSize(100_000, 300, 256, 4000));
    assertEquals(256, TaerraConfig.cacheSize(10, 300, 256, 4000));
  }
}
