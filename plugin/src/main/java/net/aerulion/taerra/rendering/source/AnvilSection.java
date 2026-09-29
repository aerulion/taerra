package net.aerulion.taerra.rendering.source;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.aerulion.taerra.rendering.block.BlockShape;
import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import net.aerulion.taerra.rendering.block.Fluids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AnvilSection {

  private static final @NotNull Map<String, Biome> BIOMES_BY_NAME = new ConcurrentHashMap<>();

  private static final byte UNKNOWN = 0;
  private static final byte YES = 1;
  private static final byte NO = 2;

  private final Material @NotNull [] palette;
  private final boolean @NotNull [] paletteAir;
  private final long @NotNull [] data;
  private final int bits;
  private final Biome @NotNull [] biomePalette;
  private final long @NotNull [] biomeData;
  private final int biomeBits;
  private final boolean allAir;

  private short @Nullable [] entries;

  private byte allFull = UNKNOWN;

  private AnvilSection(
      final Material @NotNull [] palette,
      final long @NotNull [] data,
      final int bits,
      final Biome @NotNull [] biomePalette,
      final long @NotNull [] biomeData,
      final int biomeBits) {
    super();
    this.palette = palette;
    this.data = data;
    this.bits = bits;
    this.biomePalette = biomePalette;
    this.biomeData = biomeData;
    this.biomeBits = biomeBits;
    this.paletteAir = new boolean[palette.length];
    boolean air = true;
    for (int i = 0; i < palette.length; i++) {
      this.paletteAir[i] = palette[i].isAir();
      air &= this.paletteAir[i];
    }
    this.allAir = air;
  }

  static @NotNull AnvilSection decode(final @NotNull CompoundTag sectionTag) {
    final @NotNull CompoundTag blocks = sectionTag.getCompoundOrEmpty("block_states");
    final @NotNull ListTag blockPalette = blocks.getListOrEmpty("palette");
    final Material @NotNull [] palette = new Material[Math.max(1, blockPalette.size())];
    palette[0] = Material.AIR;
    for (int i = 0; i < blockPalette.size(); i++) {
      palette[i] =
          blockPalette
              .getCompound(i)
              .map(entry -> AnvilMaterials.byName(entry.getStringOr("Name", "")))
              .orElse(Material.AIR);
    }
    final int bits = bitsOnDisk(blockPalette.size(), 4, 8);
    final long @NotNull [] data =
        bits == 0 ? new long[0] : blocks.getLongArray("data").orElse(new long[0]);

    final @NotNull CompoundTag biomes = sectionTag.getCompoundOrEmpty("biomes");
    final @NotNull ListTag biomeNames = biomes.getListOrEmpty("palette");
    final Biome @NotNull [] biomePalette = new Biome[Math.max(1, biomeNames.size())];
    biomePalette[0] = Biome.PLAINS;
    for (int i = 0; i < biomeNames.size(); i++) {
      biomePalette[i] = biome(biomeNames.getStringOr(i, ""));
    }
    final int biomeBits = bitsOnDisk(biomeNames.size(), 1, 3);
    final long @NotNull [] biomeData =
        biomeBits == 0 ? new long[0] : biomes.getLongArray("data").orElse(new long[0]);

    return new AnvilSection(palette, data, bits, biomePalette, biomeData, biomeBits);
  }

  private static @NotNull Biome biome(final @NotNull String name) {
    return BIOMES_BY_NAME.computeIfAbsent(
        name,
        key -> {
          final @Nullable NamespacedKey namespacedKey = NamespacedKey.fromString(key);
          final @Nullable Biome biome =
              namespacedKey == null
                  ? null
                  : RegistryAccess.registryAccess()
                      .getRegistry(RegistryKey.BIOME)
                      .get(namespacedKey);
          return biome == null ? Biome.PLAINS : biome;
        });
  }

  static int bitsOnDisk(final int paletteSize, final int smallestBits, final int largestSmall) {
    if (paletteSize <= 1) {
      return 0;
    }
    final int needed = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
    return needed > largestSmall ? needed : Math.max(smallestBits, needed);
  }

  static short @NotNull [] unpackAll(
      final long @NotNull [] data, final int bits, final int paletteSize) {
    final short @NotNull [] entries = new short[4096];
    final int perLong = 64 / bits;
    final long mask = (1L << bits) - 1L;
    int index = 0;
    for (int word = 0; word < data.length && index < entries.length; word++) {
      long packed = data[word];
      for (int slot = 0; slot < perLong && index < entries.length; slot++) {
        final int entry = (int) (packed & mask);
        entries[index] = (short) (entry < paletteSize ? entry : 0);
        index++;
        packed >>>= bits;
      }
    }
    return entries;
  }

  static int unpack(final long @NotNull [] data, final int bits, final int index) {
    final int perLong = 64 / bits;
    final int longIndex = index / perLong;
    if (longIndex >= data.length) {
      return 0;
    }
    return (int) (data[longIndex] >>> ((index - longIndex * perLong) * bits)) & ((1 << bits) - 1);
  }

  boolean allAir() {
    return this.allAir;
  }

  boolean allFull(final @NotNull BlockShapeResolver shapes) {
    if (this.allFull == UNKNOWN) {
      boolean full = true;
      for (final @NotNull Material material : this.palette) {
        full &= shapes.shapeOf(Fluids.substituteSubmerged(material)) == BlockShape.FULL;
      }
      this.allFull = full ? YES : NO;
    }
    return this.allFull == YES;
  }

  private int entryAt(final int localX, final int localY, final int localZ) {
    if (this.bits == 0) {
      return 0;
    }
    short @Nullable [] entries = this.entries;
    if (entries == null) {
      entries = unpackAll(this.data, this.bits, this.palette.length);
      this.entries = entries;
    }
    return entries[(localY << 8) | (localZ << 4) | localX];
  }

  @NotNull
  Material materialAt(final int localX, final int localY, final int localZ) {
    return this.palette[entryAt(localX, localY, localZ)];
  }

  boolean isAir(final int localX, final int localY, final int localZ) {
    return this.paletteAir[entryAt(localX, localY, localZ)];
  }

  @NotNull
  Biome biomeAt(final int localX, final int localY, final int localZ) {
    if (this.biomeBits == 0) {
      return this.biomePalette[0];
    }
    final int entry =
        unpack(
            this.biomeData,
            this.biomeBits,
            ((localY >> 2) << 4) | ((localZ >> 2) << 2) | (localX >> 2));
    return entry < this.biomePalette.length ? this.biomePalette[entry] : Biome.PLAINS;
  }
}
