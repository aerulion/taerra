package net.aerulion.taerra.mod.gui.widget;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.util.PlayerInfos;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Player;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerList extends PanelList<PlayerList.Entry> {

  private static final int ROW = 24;

  private final WebMap map;
  private final Settings settings;
  private final Supplier<@Nullable String> world;
  private final Function<String, Component> worldName;
  private final Consumer<Player> onPick;
  private @Nullable String selected;

  public PlayerList(
      final WebMap map,
      final Settings settings,
      final Supplier<@Nullable String> world,
      final Function<String, Component> worldName,
      final Consumer<Player> onPick) {
    super(WIDTH, ROW);
    this.map = map;
    this.settings = settings;
    this.world = world;
    this.worldName = worldName;
    this.onPick = onPick;
  }

  public void refresh() {
    final @Nullable String world = this.world.get();
    final boolean all = this.settings.allWorldPlayers();
    final @NotNull Comparator<Player> elsewhere =
        Comparator.comparing(player -> !player.world().equals(world));
    replaceEntries(
        this.map.players().stream()
            .filter(player -> all || player.world().equals(world))
            .sorted(
                elsewhere
                    .thenComparing(player -> this.worldName.apply(player.world()).getString())
                    .thenComparing(
                        player -> player.displayName().getString(), String.CASE_INSENSITIVE_ORDER))
            .map(Entry::new)
            .toList());
    select(this.selected);
  }

  public void select(final @Nullable String uuid) {
    this.selected = uuid;
    setSelected(
        children().stream()
            .filter(entry -> entry.player.uuid().equals(uuid))
            .findFirst()
            .orElse(null));
  }

  public int count() {
    return children().size();
  }

  public final class Entry extends ObjectSelectionList.Entry<Entry> {

    private final Player player;

    private Entry(final Player player) {
      this.player = player;
    }

    @Override
    public void extractContent(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      final @NotNull Font font = Minecraft.getInstance().font;
      final int x = getContentX() + 2;
      final int y = getContentYMiddle() - 8;
      final @Nullable PlayerInfo info =
          this.player.anonymous() ? null : PlayerInfos.find(this.player.uuid());
      PlayerFaceExtractor.extractRenderState(
          graphics, info != null ? info.getSkin() : DefaultPlayerSkin.getDefaultSkin(), x, y, 16);
      graphics.text(
          font,
          this.player
              .displayName()
              .copy()
              .withStyle(this.player.hidden() ? ChatFormatting.GRAY : ChatFormatting.WHITE),
          x + 20,
          y,
          0xFFFFFFFF,
          true);
      final boolean elsewhere = !this.player.world().equals(PlayerList.this.world.get());
      final Component where =
          elsewhere
              ? PlayerList.this.worldName.apply(this.player.world())
              : this.player.hidden()
                  ? Component.translatable("taerra.players.hidden")
                  : this.player.coordinates();
      graphics.text(
          font, where.copy().withStyle(ChatFormatting.GRAY), x + 20, y + 9, 0xFFFFFFFF, false);
      if (hovered) {
        graphics.setTooltipForNextFrame(
            font,
            Component.translatable(
                elsewhere ? "taerra.players.follow_elsewhere" : "taerra.players.follow",
                this.player.displayName(),
                PlayerList.this.worldName.apply(this.player.world())),
            mouseX,
            mouseY);
      }
    }

    @Override
    public @NotNull Component getNarration() {
      return this.player.displayName();
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
      PlayerList.this.onPick.accept(this.player);
      return true;
    }
  }
}
