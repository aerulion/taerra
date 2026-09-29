package net.aerulion.taerra.model;

public record ChunkCoordinate(int x, int z) {

  public static long key(final int x, final int z) {
    return ((long) x << 32) ^ (z & 0xffffffffL);
  }

  public static int keyX(final long key) {
    return (int) (key >> 32);
  }

  public static int keyZ(final long key) {
    return (int) key;
  }

  public long toKey() {
    return key(this.x, this.z);
  }
}
