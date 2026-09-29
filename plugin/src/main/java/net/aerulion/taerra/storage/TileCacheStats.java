package net.aerulion.taerra.storage;

public record TileCacheStats(long size, long hits, long misses, long evictions) {

  public double hitRate() {
    final long total = this.hits + this.misses;
    return total == 0 ? 0.0 : (double) this.hits / total;
  }
}
