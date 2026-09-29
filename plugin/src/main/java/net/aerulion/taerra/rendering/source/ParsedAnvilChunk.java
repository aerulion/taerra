package net.aerulion.taerra.rendering.source;

import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ParsedAnvilChunk implements ColumnSource {

  private final int minHeight;
  private final int maxHeight;
  private final int minSectionY;
  private final AnvilSection @NotNull [] sections;

  private ParsedAnvilChunk(
      final int minHeight, final int maxHeight, final AnvilSection @NotNull [] sections) {
    super();
    this.minHeight = minHeight;
    this.maxHeight = maxHeight;
    this.minSectionY = minHeight >> 4;
    this.sections = sections;
  }

  public static @NotNull ParsedAnvilChunk fromNbt(
      final @NotNull CompoundTag chunkTag, final int minHeight, final int maxHeight) {
    final int minSectionY = minHeight >> 4;
    final AnvilSection @NotNull [] sections = new AnvilSection[(maxHeight - minHeight) >> 4];
    final @NotNull ListTag sectionTags = chunkTag.getListOrEmpty("sections");
    for (int i = 0; i < sectionTags.size(); i++) {
      final @Nullable CompoundTag sectionTag = sectionTags.getCompound(i).orElse(null);
      if (sectionTag == null) {
        continue;
      }
      final int index = sectionTag.getByteOr("Y", (byte) 0) - minSectionY;
      if (index >= 0 && index < sections.length) {
        sections[index] = AnvilSection.decode(sectionTag);
      }
    }
    return new ParsedAnvilChunk(minHeight, maxHeight, sections);
  }

  @Override
  public int minHeight() {
    return this.minHeight;
  }

  @Override
  public int maxHeight() {
    return this.maxHeight;
  }

  @Override
  public int highestNonAirY(final int localX, final int localZ) {
    for (int sectionIndex = this.sections.length - 1; sectionIndex >= 0; sectionIndex--) {
      final @Nullable AnvilSection section = this.sections[sectionIndex];
      if (section == null || section.allAir()) {
        continue;
      }
      final int sectionBaseY = (sectionIndex + this.minSectionY) << 4;
      for (int localY = 15; localY >= 0; localY--) {
        if (!section.isAir(localX, localY, localZ)) {
          return sectionBaseY + localY;
        }
      }
    }
    return EMPTY_COLUMN;
  }

  @Override
  public boolean isSectionAllAir(final int y) {
    final @Nullable AnvilSection section = sectionAt(y);
    return section == null || section.allAir();
  }

  @Override
  public boolean isSectionAllFull(final int y, final @NotNull BlockShapeResolver shapes) {
    final @Nullable AnvilSection section = sectionAt(y);
    return section != null && section.allFull(shapes);
  }

  @Override
  public @NotNull Material materialAt(final int localX, final int y, final int localZ) {
    final @Nullable AnvilSection section = sectionAt(y);
    return section == null ? Material.AIR : section.materialAt(localX, y & 15, localZ);
  }

  @Override
  public @NotNull Biome biomeAt(final int localX, final int y, final int localZ) {
    final @Nullable AnvilSection section =
        sectionAt(Math.max(this.minHeight, Math.min(y, this.maxHeight - 1)));
    return section == null ? Biome.PLAINS : section.biomeAt(localX, y & 15, localZ);
  }

  private @Nullable AnvilSection sectionAt(final int y) {
    final int sectionIndex = (y >> 4) - this.minSectionY;
    return sectionIndex < 0 || sectionIndex >= this.sections.length
        ? null
        : this.sections[sectionIndex];
  }
}
