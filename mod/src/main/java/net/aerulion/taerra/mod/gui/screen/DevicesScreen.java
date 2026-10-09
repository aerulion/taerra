package net.aerulion.taerra.mod.gui.screen;

import java.time.Duration;
import java.util.List;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Device;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DevicesScreen extends Screen {

  private static final int ROW = 26;
  private static final int ROW_WIDTH = 300;
  private static final int BUTTON_WIDTH = 70;

  private final @NotNull Screen parent;
  private final @NotNull WebMap map;
  private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private @Nullable DeviceList list;
  private @Nullable List<Device> devices;
  private boolean loaded;

  public DevicesScreen(final @NotNull Screen parent, final @NotNull WebMap map) {
    super(Component.translatable("taerra.devices"));
    this.parent = parent;
    this.map = map;
  }

  static @NotNull Component name(final @NotNull Device device) {
    if (device.game()) {
      return Component.translatable("taerra.devices.game");
    }
    final @NotNull Component browser =
        device.browser() == null
            ? Component.translatable("taerra.devices.browser")
            : Component.literal(device.browser());
    return device.os() == null
        ? browser
        : Component.translatable("taerra.devices.on", browser, device.os());
  }

  static @NotNull Component seen(final @NotNull Device device) {
    if (device.game()) {
      return Component.translatable("taerra.devices.here");
    }
    final @NotNull Duration since =
        Duration.ofMillis(Math.max(0, System.currentTimeMillis() - device.seen()));
    if (since.toMinutes() < 1) {
      return Component.translatable("taerra.devices.now");
    }
    if (since.toHours() < 1) {
      return Component.translatable("taerra.devices.minutes", since.toMinutes());
    }
    return since.toDays() < 1
        ? Component.translatable("taerra.devices.hours", since.toHours())
        : Component.translatable("taerra.devices.days", since.toDays());
  }

  @Override
  protected void init() {
    this.layout.addTitleHeader(this.title, this.font);
    this.list = this.layout.addToContents(new DeviceList());
    this.layout.addToFooter(
        Button.builder(CommonComponents.GUI_DONE, _ -> onClose()).width(200).build());
    this.layout.visitWidgets(this::addRenderableWidget);
    repositionElements();
    if (this.loaded) {
      this.list.show(this.devices);
    } else {
      refresh();
    }
  }

  @Override
  protected void repositionElements() {
    this.layout.arrangeElements();
    if (this.list != null) {
      this.list.updateSize(this.width, this.layout);
    }
  }

  @Override
  public void onClose() {
    this.minecraft.setScreenAndShow(this.parent);
  }

  @Override
  public void extractRenderState(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    if (!this.loaded || this.devices == null) {
      graphics.centeredText(
          this.font,
          Component.translatable(
              this.loaded ? "taerra.devices.unavailable" : "taerra.devices.loading"),
          this.width / 2,
          this.height / 2 - 4,
          0xFFA0A0A0);
    }
  }

  private void refresh() {
    this.map.devices(
        devices -> {
          this.devices = devices;
          this.loaded = true;
          if (this.list != null) {
            this.list.show(devices);
          }
        });
  }

  private final class DeviceList extends ContainerObjectSelectionList<DeviceList.Entry> {

    private DeviceList() {
      super(Minecraft.getInstance(), 0, 0, 0, ROW);
    }

    private void show(final @Nullable List<Device> devices) {
      replaceEntries(devices == null ? List.of() : devices.stream().map(Entry::new).toList());
    }

    @Override
    public int getRowWidth() {
      return ROW_WIDTH;
    }

    private final class Entry extends ContainerObjectSelectionList.Entry<Entry> {

      private final @NotNull Component name;
      private final @NotNull Component seen;
      private final @Nullable Button logout;

      private Entry(final @NotNull Device device) {
        this.name = name(device);
        this.seen = seen(device);
        this.logout =
            device.game() || device.current()
                ? null
                : Button.builder(
                        Component.translatable("taerra.devices.logout"),
                        button -> {
                          button.active = false;
                          DevicesScreen.this.map.logOutDevice(device.id(), _ -> refresh());
                        })
                    .width(BUTTON_WIDTH)
                    .build();
      }

      @Override
      public void extractContent(
          final GuiGraphicsExtractor graphics,
          final int mouseX,
          final int mouseY,
          final boolean hovered,
          final float partialTick) {
        final @NotNull Font font = Minecraft.getInstance().font;
        final int top = getContentYMiddle() - font.lineHeight;
        graphics.text(font, this.name, getContentX(), top, 0xFFFFFFFF, true);
        graphics.text(font, this.seen, getContentX(), top + font.lineHeight + 1, 0xFFA0A0A0, true);
        if (this.logout != null) {
          this.logout.setPosition(
              getContentRight() - BUTTON_WIDTH, getContentYMiddle() - this.logout.getHeight() / 2);
          this.logout.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
      }

      @Override
      public @NotNull List<? extends GuiEventListener> children() {
        return this.logout == null ? List.of() : List.of(this.logout);
      }

      @Override
      public @NotNull List<? extends NarratableEntry> narratables() {
        return this.logout == null ? List.of() : List.of(this.logout);
      }
    }
  }
}
