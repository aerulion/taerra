package net.aerulion.taerra.mod.gui.widget;

import java.util.function.Function;
import net.aerulion.taerra.mod.web.model.Progress;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenderProgressOverlay {

  private static final Identifier BAR_BACKGROUND =
      Identifier.withDefaultNamespace("boss_bar/white_background");
  private static final Identifier BAR_PROGRESS =
      Identifier.withDefaultNamespace("boss_bar/white_progress");
  private static final int BAR_WIDTH = 182;

  private RenderProgressOverlay() {}

  public static void draw(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull Font font,
      final @Nullable Progress progress,
      final @NotNull Function<String, Component> worldName,
      final int middle,
      final int top) {
    if (progress == null) {
      return;
    }
    final Progress.@Nullable Render render = progress.render();
    final long pending = (long) progress.queued() + progress.inFlight();
    final @NotNull Component title;
    final @NotNull Component detail;
    double percent = -1;
    if (render != null) {
      percent = Math.clamp(render.percent(), 0, 100);
      title =
          Component.translatable(
              render.kind().equals("area") ? "taerra.render.area" : "taerra.render.full",
              worldName.apply(render.world()),
              (int) Math.floor(percent));
      if (render.phase().equals("scanning")) {
        detail =
            render.totalRegions() > 0
                ? Component.translatable(
                    "taerra.render.scanning", render.scannedRegions(), render.totalRegions())
                : Component.translatable("taerra.render.starting");
      } else {
        final @Nullable Long remaining = render.remainingMillis();
        final @NotNull Component drawing =
            Component.translatable(
                "taerra.render.drawing", render.completedTiles(), render.totalTiles());
        detail = remaining == null ? drawing : drawing.copy().append(remaining(remaining));
      }
    } else if (pending > 0) {
      title = Component.translatable("taerra.render.updating");
      detail = Component.translatable("taerra.render.changes", pending);
    } else {
      return;
    }
    graphics.centeredText(font, title, middle, top + 4, 0xFFFFFFFF);
    int below = top + 15;
    if (percent >= 0) {
      final int left = middle - BAR_WIDTH / 2;
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BACKGROUND, left, below, BAR_WIDTH, 5);
      graphics.blitSprite(
          RenderPipelines.GUI_TEXTURED,
          BAR_PROGRESS,
          BAR_WIDTH,
          5,
          0,
          0,
          left,
          below,
          (int) Math.round(BAR_WIDTH * percent / 100),
          5);
      below += 8;
    }
    graphics.centeredText(font, detail, middle, below, 0xFFA0A0A0);
  }

  private static @NotNull Component remaining(final long millis) {
    final long minutes = Math.max(1, Math.round(millis / 60000.0));
    return minutes < 60
        ? Component.translatable("taerra.render.remaining.minutes", minutes)
        : Component.translatable("taerra.render.remaining.hours", minutes / 60, minutes % 60);
  }
}
