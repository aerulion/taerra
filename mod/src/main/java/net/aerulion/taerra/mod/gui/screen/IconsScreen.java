package net.aerulion.taerra.mod.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.aerulion.taerra.mod.gui.widget.Glyph;
import net.aerulion.taerra.mod.gui.widget.IconButton;
import net.aerulion.taerra.mod.map.MapIcons;
import net.aerulion.taerra.mod.util.FileDialogs;
import net.aerulion.taerra.mod.util.Pixels;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.WebMapListener;
import net.aerulion.taerra.mod.web.model.MapIcon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IconsScreen extends Screen implements WebMapListener {

  static final int MAX = 256;
  private static final int MAX_BYTES = 16 * 1024;
  private static final int MAX_NAME = 32;
  private static final int ROW = 26;
  private static final int ICON = 16;
  private static final int TOOLS_Y = 24;
  private static final Pattern NOT_NAME = Pattern.compile("[^\\p{L}\\p{N} _.,:'&()#!?+-]");

  private final @NotNull Screen parent;
  private final @NotNull WebMap map;
  private @NotNull String query = "";
  private @NotNull String shown = "";
  private @Nullable String armed;
  private @NotNull Component status = Component.empty();
  private @Nullable IconList list;

  public IconsScreen(final @NotNull Screen parent, final @NotNull WebMap map) {
    super(Component.translatable("taerra.icons"));
    this.parent = parent;
    this.map = map;
  }

  private static @NotNull String fold(final @NotNull String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  private static @NotNull String nameOf(final @NotNull Path path) {
    final @NotNull String name =
        NOT_NAME
            .matcher(
                Normalizer.normalize(
                    path.getFileName()
                        .toString()
                        .replaceFirst("(?i)\\.png$", "")
                        .replaceAll("[_-]+", " "),
                    Normalizer.Form.NFC))
            .replaceAll("")
            .strip();
    final @NotNull String cut = name.substring(0, Math.min(name.length(), MAX_NAME)).strip();
    return cut.codePoints().anyMatch(Character::isLetterOrDigit)
        ? cut
        : Component.translatable("taerra.icons.unnamed").getString();
  }

  static @NotNull String problem(final int status) {
    return switch (status) {
      case 400 -> "taerra.icons.bad_name";
      case 409 -> "taerra.icons.taken";
      case 415 -> "taerra.icons.wrong_size";
      case 422 -> "taerra.icons.full";
      default -> "taerra.icons.failed";
    };
  }

  private int rowWidth() {
    return Math.min(this.width - 24, 460);
  }

  private @NotNull List<MapIcon> uploads() {
    return this.map.icons().stream().filter(MapIcon::uploaded).toList();
  }

  @Override
  public void onClose() {
    this.map.removeListener(this);
    this.minecraft.setScreenAndShow(this.parent);
  }

  @Override
  public void iconsChanged() {
    refresh(false);
  }

  @Override
  protected void init() {
    this.map.removeListener(this);
    this.map.addListener(this);
    final int left = (this.width - rowWidth()) / 2;
    final int button = 90;
    final @NotNull EditBox filter =
        new EditBox(
            this.font,
            left,
            TOOLS_Y,
            rowWidth() - 2 * button - 8,
            20,
            Component.translatable("taerra.waypoints.filter"));
    filter.setHint(Component.translatable("taerra.waypoints.filter"));
    filter.setValue(this.query);
    filter.setResponder(
        value -> {
          this.query = value;
          refresh(true);
        });
    addRenderableWidget(filter);
    final @NotNull Button upload =
        Button.builder(
                Component.translatable("taerra.icons.upload"),
                _ -> FileDialogs.openImages(paths -> upload(paths, 0, 0, new ArrayList<>())))
            .bounds(left + rowWidth() - button, TOOLS_Y, button, 20)
            .tooltip(Tooltip.create(Component.translatable("taerra.icons.hint")))
            .build();
    upload.active = uploads().size() < MAX;
    addRenderableWidget(upload);
    final @NotNull Button draw =
        Button.builder(
                Component.translatable("taerra.icons.draw"),
                _ ->
                    this.minecraft.setScreenAndShow(
                        new IconEditorScreen(
                            this, this.map, null, message -> show(message, false))))
            .bounds(left + rowWidth() - 2 * button - 4, TOOLS_Y, button, 20)
            .build();
    draw.active = uploads().size() < MAX;
    addRenderableWidget(draw);
    addRenderableWidget(
        Button.builder(CommonComponents.GUI_DONE, _ -> onClose())
            .bounds(this.width / 2 - 75, this.height - 28, 150, 20)
            .build());
    final int top = TOOLS_Y + 38;
    this.list = addRenderableWidget(new IconList(top, Math.max(ROW, this.height - 54 - top)));
    refresh(true);
  }

  @Override
  public void extractRenderState(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    graphics.centeredText(
        this.font,
        this.title
            .copy()
            .append(
                Component.literal("  " + uploads().size() + " / " + MAX)
                    .withStyle(ChatFormatting.GRAY)),
        this.width / 2,
        8,
        0xFFFFFFFF);
    graphics.centeredText(
        this.font,
        Component.translatable("taerra.icons.hint"),
        this.width / 2,
        TOOLS_Y + 25,
        0xFF909090);
    if (this.list != null && this.list.children().isEmpty()) {
      graphics.centeredText(
          this.font,
          Component.translatable(
              uploads().isEmpty() ? "taerra.icons.none" : "taerra.icons.no_match"),
          this.width / 2,
          TOOLS_Y + 52,
          CommonColors.LIGHT_GRAY);
    }
    graphics.centeredText(this.font, this.status, this.width / 2, this.height - 42, 0xFFFFFFFF);
  }

  @Override
  public void onFilesDrop(final @NotNull List<Path> paths) {
    upload(
        paths.stream()
            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
            .toList(),
        0,
        0,
        new ArrayList<>());
  }

  private void refresh(final boolean always) {
    final @Nullable IconList list = this.list;
    if (list == null) {
      return;
    }
    final @NotNull String query = fold(this.query.strip());
    final @NotNull List<MapIcon> icons =
        uploads().stream()
            .filter(icon -> query.isEmpty() || fold(String.valueOf(icon.label())).contains(query))
            .sorted(Comparator.comparing((MapIcon icon) -> fold(String.valueOf(icon.label()))))
            .toList();
    final @NotNull String shown = icons + "|" + this.armed;
    if (always || !shown.equals(this.shown)) {
      this.shown = shown;
      list.show(icons);
    }
  }

  private void show(final @NotNull Component status, final boolean failed) {
    this.status = status.copy().withStyle(failed ? ChatFormatting.RED : ChatFormatting.GREEN);
  }

  private @Nullable String read(final @NotNull Path path, final byte @NotNull [][] png) {
    final @NotNull String file = path.getFileName().toString();
    try {
      if (Files.size(path) > MAX_BYTES) {
        return Component.translatable("taerra.icons.too_big", file).getString();
      }
      png[0] = Files.readAllBytes(path);
    } catch (final IOException e) {
      return Component.translatable("taerra.icons.unreadable", file).getString();
    }
    return Pixels.read(png[0]) == null
        ? Component.translatable("taerra.icons.not_icon", file).getString()
        : null;
  }

  private void upload(
      final @NotNull List<Path> paths,
      final int index,
      final int added,
      final @NotNull List<String> problems) {
    if (index >= paths.size()) {
      if (!paths.isEmpty()) {
        final @NotNull List<String> parts = new ArrayList<>();
        if (added > 0) {
          parts.add(Component.translatable("taerra.icons.uploaded", added).getString());
        }
        parts.addAll(problems);
        show(Component.literal(String.join(" ", parts)), !problems.isEmpty());
      }
      return;
    }
    final @NotNull Path path = paths.get(index);
    final byte @NotNull [][] png = new byte[1][];
    final @Nullable String problem = read(path, png);
    if (problem != null) {
      problems.add(problem);
      upload(paths, index + 1, added, problems);
      return;
    }
    send(paths, index, added, problems, nameOf(path), png[0], 1);
  }

  private void send(
      final @NotNull List<Path> paths,
      final int index,
      final int added,
      final @NotNull List<String> problems,
      final @NotNull String base,
      final byte @NotNull [] png,
      final int attempt) {
    final @NotNull String name =
        attempt == 1
            ? base
            : base.substring(0, Math.min(base.length(), MAX_NAME - 3)).strip() + " " + attempt;
    this.map.uploadIcon(
        name,
        png,
        (status, _) -> {
          if (status == 409 && attempt < 9) {
            send(paths, index, added, problems, base, png, attempt + 1);
            return;
          }
          if (status / 100 == 2) {
            upload(paths, index + 1, added + 1, problems);
            return;
          }
          problems.add(Component.translatable(problem(status), base, MAX).getString());
          upload(paths, status == 422 ? paths.size() : index + 1, added, problems);
        });
  }

  private void replace(final @NotNull MapIcon icon, final @NotNull List<Path> paths) {
    if (paths.isEmpty()) {
      return;
    }
    final byte @NotNull [][] png = new byte[1][];
    final @Nullable String problem = read(paths.getFirst(), png);
    if (problem != null) {
      show(Component.literal(problem), true);
      return;
    }
    this.map.updateIcon(
        icon,
        null,
        png[0],
        status ->
            show(
                status / 100 == 2
                    ? Component.translatable("taerra.icons.replaced", icon.displayName())
                    : Component.translatable(problem(status), icon.displayName(), MAX),
                status / 100 != 2));
  }

  private void rename(final @NotNull MapIcon icon, final @NotNull String value) {
    final @NotNull String name = value.strip();
    if (name.equals(icon.label())) {
      return;
    }
    if (name.codePoints().noneMatch(Character::isLetterOrDigit)) {
      show(Component.translatable("taerra.icons.bad_name"), true);
      refresh(true);
      return;
    }
    this.map.updateIcon(
        icon,
        name,
        null,
        status -> {
          if (status / 100 != 2) {
            show(Component.translatable(problem(status), name, MAX), true);
            refresh(true);
          }
        });
  }

  private void delete(final @NotNull MapIcon icon) {
    if (!icon.id().equals(this.armed)) {
      this.armed = icon.id();
      refresh(true);
      return;
    }
    this.armed = null;
    this.map.deleteIcon(
        icon,
        status -> {
          if (status / 100 != 2) {
            show(Component.translatable("taerra.icons.failed"), true);
          }
        });
  }

  private final class IconList extends ContainerObjectSelectionList<IconList.Entry> {

    private IconList(final int top, final int height) {
      super(Minecraft.getInstance(), IconsScreen.this.width, height, top, ROW);
    }

    private void show(final @NotNull List<MapIcon> icons) {
      replaceEntries(icons.stream().map(Entry::new).toList());
    }

    @Override
    public int getRowWidth() {
      return rowWidth();
    }

    private final class Entry extends ContainerObjectSelectionList.Entry<Entry> {

      private final @NotNull MapIcon icon;
      private final @NotNull EditBox name;
      private final @NotNull List<GuiEventListener> children = new ArrayList<>();
      private final @NotNull List<IconButton> buttons = new ArrayList<>();
      private final @NotNull Component meta;

      private Entry(final @NotNull MapIcon icon) {
        this.icon = icon;
        final int uses = icon.uses() == null ? 0 : icon.uses();
        this.meta =
            uses == 0
                ? Component.translatable("taerra.icons.unused")
                : Component.translatable(
                    uses == 1 ? "taerra.icons.uses.one" : "taerra.icons.uses.other", uses);
        this.name =
            new EditBox(
                IconsScreen.this.font,
                0,
                0,
                160,
                16,
                Component.translatable("taerra.icons.rename", icon.displayName())) {
              @Override
              public boolean keyPressed(final @NotNull KeyEvent event) {
                if (event.key() == InputConstants.KEY_RETURN
                    || event.key() == InputConstants.KEY_NUMPADENTER) {
                  rename(icon, getValue());
                  return true;
                }
                return super.keyPressed(event);
              }

              @Override
              public void setFocused(final boolean focused) {
                if (isFocused() && !focused) {
                  rename(icon, getValue());
                }
                super.setFocused(focused);
              }
            };
        this.name.setMaxLength(MAX_NAME);
        this.name.setValue(String.valueOf(icon.label()));
        this.name.setResponder(
            value -> {
              final @NotNull String clean = NOT_NAME.matcher(value).replaceAll("");
              if (!clean.equals(value)) {
                this.name.setValue(clean);
              }
            });
        this.children.add(this.name);
        button(
            WaypointListScreen.EDIT,
            "taerra.icons.draw.edit",
            () -> {
              if (IconsScreen.this.map.iconPixels(icon) != null) {
                IconsScreen.this.minecraft.setScreenAndShow(
                    new IconEditorScreen(
                        IconsScreen.this,
                        IconsScreen.this.map,
                        icon,
                        message -> IconsScreen.this.show(message, false)));
              }
            });
        button(
            IconButton.CLOCKWISE,
            "taerra.icons.replace",
            () -> FileDialogs.openImages(paths -> replace(icon, paths)));
        button(
            WaypointListScreen.DELETE,
            icon.id().equals(IconsScreen.this.armed)
                ? "taerra.waypoint.confirm_delete"
                : "taerra.waypoint.delete",
            () -> delete(icon));
      }

      private void button(
          final @NotNull Glyph glyph, final @NotNull String label, final @NotNull Runnable run) {
        final @NotNull IconButton button =
            new IconButton(ICON, glyph, Component.translatable(label), run);
        button.setTooltip(Tooltip.create(Component.translatable(label)));
        this.buttons.add(button);
        this.children.add(button);
      }

      @Override
      public void extractContent(
          final GuiGraphicsExtractor graphics,
          final int mouseX,
          final int mouseY,
          final boolean hovered,
          final float partialTick) {
        final @NotNull Font font = IconsScreen.this.font;
        final int middle = getContentYMiddle();
        int x = getContentX();
        graphics.fill(x, middle - 10, x + 20, middle + 10, 0xFF1E1E1F);
        final @Nullable Identifier texture = MapIcons.icon(IconsScreen.this.map, this.icon.id());
        if (texture != null) {
          graphics.blit(texture, x + 2, middle - 8, x + 18, middle + 8, 0, 1, 0, 1);
        }
        x += 26;
        int right = getContentRight();
        for (int i = this.buttons.size() - 1; i >= 0; i--) {
          final @NotNull IconButton button = this.buttons.get(i);
          right -= ICON;
          button.setPosition(right, middle - ICON / 2);
          button.extractRenderState(graphics, mouseX, mouseY, partialTick);
          right -= 2;
        }
        final int metaWidth = font.width(this.meta);
        final int room = Math.max(40, right - x - metaWidth - 12);
        this.name.setRectangle(room, 16, x, middle - 8);
        this.name.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, this.meta, x + room + 6, middle - 4, 0xFF909090, false);
      }

      @Override
      public @NotNull List<? extends GuiEventListener> children() {
        return this.children;
      }

      @Override
      public @NotNull List<? extends NarratableEntry> narratables() {
        return this.buttons;
      }
    }
  }
}
