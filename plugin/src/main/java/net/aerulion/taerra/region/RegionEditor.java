package net.aerulion.taerra.region;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.rendering.RenderService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionEditor {

  public static final int MAX_NAME_LENGTH = 48;
  public static final int MAX_CHUNKS = 65536;

  private final @NotNull Plugin plugin;
  private final @NotNull MapRegions regions;
  private final @NotNull RegionDiscoveries discoveries;
  private final @NotNull RenderService renders;
  private final @NotNull Runnable changed;

  public RegionEditor(
      final @NotNull Plugin plugin,
      final @NotNull MapRegions regions,
      final @NotNull RegionDiscoveries discoveries,
      final @NotNull RenderService renders,
      final @NotNull Runnable changed) {
    super();
    this.plugin = plugin;
    this.regions = regions;
    this.discoveries = discoveries;
    this.renders = renders;
    this.changed = changed;
  }

  public @NotNull CompletableFuture<Result> save(
      final @NotNull String worldName,
      final @Nullable Integer id,
      final @NotNull String name,
      final @NotNull RegionFog fog,
      final boolean discoverable,
      final @NotNull Collection<Long> chunks) {
    final @NotNull String trimmed = name.strip();
    if (trimmed.length() > MAX_NAME_LENGTH) {
      return CompletableFuture.completedFuture(Problem.NAME_TOO_LONG);
    }
    if (chunks.isEmpty() || chunks.size() > MAX_CHUNKS) {
      return CompletableFuture.completedFuture(Problem.EMPTY);
    }
    if (!RegionShapes.connected(chunks)) {
      return CompletableFuture.completedFuture(Problem.SPLIT);
    }
    final @NotNull LongSet painted = new LongOpenHashSet(chunks);
    return onMainThread(
        () -> {
          final @Nullable World world = WorldKeys.resolve(worldName);
          if (world == null) {
            return Problem.NOT_FOUND;
          }
          synchronized (this) {
            final @Nullable MapRegion previous = id == null ? null : this.regions.get(id);
            if (id != null && (previous == null || !previous.world().equals(worldName))) {
              return Problem.NOT_FOUND;
            }
            for (final @NotNull MapRegion other : this.regions.in(worldName)) {
              if (other.id() != (id == null ? 0 : id)
                  && painted.longStream().anyMatch(chunk -> other.chunks().contains(chunk))) {
                return Problem.OVERLAP;
              }
            }
            final @NotNull MapRegion region =
                previous == null
                    ? MapRegion.of(
                        this.regions.reserveId(), worldName, trimmed, fog, discoverable, painted)
                    : previous.with(trimmed, fog, discoverable, painted);
            return apply(world, region, rendered(previous, region), () -> this.regions.put(region));
          }
        });
  }

  public @NotNull CompletableFuture<Result> delete(final int id) {
    return onMainThread(
        () -> {
          synchronized (this) {
            final @Nullable MapRegion region = this.regions.get(id);
            final @Nullable World world = region == null ? null : WorldKeys.resolve(region.world());
            if (region == null || world == null) {
              return Problem.NOT_FOUND;
            }
            return apply(
                world,
                region,
                region.fog().fogged() ? region.chunks() : Set.of(),
                () -> {
                  this.regions.remove(id);
                  this.discoveries.forget(id);
                });
          }
        });
  }

  public boolean resetDiscoveries(final int id) {
    if (this.regions.get(id) == null) {
      return false;
    }
    this.discoveries.forget(id);
    this.changed.run();
    return true;
  }

  private @NotNull Result apply(
      final @NotNull World world,
      final @NotNull MapRegion region,
      final @NotNull Set<Long> rendered,
      final @NotNull Runnable apply) {
    final @NotNull Runnable applyAndNotify =
        () -> {
          apply.run();
          this.changed.run();
        };
    if (rendered.isEmpty()) {
      applyAndNotify.run();
      return new Saved(region, null);
    }
    final @NotNull RenderService.Start start =
        this.renders.regions(world, rendered, applyAndNotify);
    return start instanceof RenderService.Refused(final RenderService.Refusal reason)
        ? reason == RenderService.Refusal.BUSY ? Problem.BUSY : Problem.WORLD_DISABLED
        : new Saved(region, start);
  }

  static @NotNull Set<Long> rendered(
      final @Nullable MapRegion previous, final @NotNull MapRegion region) {
    final boolean was = previous != null && previous.fog().fogged();
    final boolean is = region.fog().fogged();
    final @NotNull Set<Long> changed = new HashSet<>();
    if (previous != null && was && (!is || previous.fog() != region.fog())) {
      changed.addAll(previous.chunks());
    }
    if (is && (!was || previous.fog() != region.fog())) {
      changed.addAll(region.chunks());
    }
    if (was && is && previous.fog() == region.fog()) {
      for (final long chunk : previous.chunks()) {
        if (!region.chunks().contains(chunk)) {
          changed.add(chunk);
        }
      }
      for (final long chunk : region.chunks()) {
        if (!previous.chunks().contains(chunk)) {
          changed.add(chunk);
        }
      }
    }
    return changed;
  }

  private <T> @NotNull CompletableFuture<T> onMainThread(final @NotNull Supplier<T> task) {
    final @NotNull CompletableFuture<T> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              try {
                result.complete(task.get());
              } catch (final RuntimeException e) {
                result.completeExceptionally(e);
              }
            });
    return result;
  }

  public sealed interface Result permits Problem, Saved {}

  public enum Problem implements Result {
    NOT_FOUND,
    EMPTY,
    SPLIT,
    OVERLAP,
    NAME_TOO_LONG,
    BUSY,
    WORLD_DISABLED
  }

  public record Saved(@NotNull MapRegion region, RenderService.@Nullable Start render)
      implements Result {}
}
