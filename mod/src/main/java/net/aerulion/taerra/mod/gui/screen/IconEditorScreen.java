package net.aerulion.taerra.mod.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.aerulion.taerra.mod.gui.widget.ColorField;
import net.aerulion.taerra.mod.gui.widget.FooterRows;
import net.aerulion.taerra.mod.gui.widget.IconButton;
import net.aerulion.taerra.mod.gui.widget.ScrollingContents;
import net.aerulion.taerra.mod.map.MapIcons;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.util.Pixels;
import net.aerulion.taerra.mod.util.Shortcuts;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.MapIcon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IconEditorScreen extends Screen {

  private static final int WIDTH = 200;
  private static final int MAX_NAME = 32;
  private static final int CELL = 13;
  private static final int HISTORY = 200;
  private static final int SIDE = 79;
  private static final String WHITE = "#ffffff";
  private static final int[] PREVIEWS = {0xF9FFFE, 0x3AB3DA, 0x1D1D21};
  private static final Pattern NOT_NAME = Pattern.compile("[^\\p{L}\\p{N} _.,:'&()#!?+-]");

  private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private final @NotNull Screen parent;
  private final @NotNull WebMap map;
  private final @Nullable MapIcon icon;
  private final @NotNull Consumer<Component> saved;
  private final @NotNull ColorField colors;
  private final @NotNull List<int[]> history = new ArrayList<>();
  private final @NotNull List<int[]> future = new ArrayList<>();
  private int[] pixels;
  private @NotNull String color = WHITE;
  private @NotNull String name = "";
  private @NotNull Component error = Component.empty();
  private boolean erasing;
  private boolean armed;
  private boolean saving;
  private int footerWidth;
  private @Nullable ScrollingContents scroll;
  private int @Nullable [] before;
  private int stroke;
  private int last = -1;
  private @Nullable IconButton undo;
  private @Nullable IconButton redo;
  private @Nullable MultiLineTextWidget tint;

  public IconEditorScreen(
      final @NotNull Screen parent,
      final @NotNull WebMap map,
      final @Nullable MapIcon icon,
      final @NotNull Consumer<Component> saved) {
    super(
        icon == null
            ? Component.translatable("taerra.icon_editor.new")
            : Component.translatable("taerra.icon_editor.edit", icon.displayName()));
    this.parent = parent;
    this.map = map;
    this.icon = icon;
    this.saved = saved;
    final int @Nullable [] known = icon == null ? null : map.iconPixels(icon);
    this.pixels = known == null ? new int[Pixels.ICON * Pixels.ICON] : known.clone();
    this.colors = new ColorField(WIDTH, WHITE, value -> this.color = value);
  }

  @Override
  public void onClose() {
    if (!this.history.isEmpty() && !this.armed) {
      this.armed = true;
      rebuildWidgets();
      return;
    }
    this.minecraft.setScreenAndShow(this.parent);
  }

  @Override
  protected void init() {
    this.layout.removeChildren();
    this.layout.addTitleHeader(this.title, this.font);
    final @NotNull LinearLayout contents = LinearLayout.vertical().spacing(6);
    @Nullable EditBox name = null;
    if (this.icon == null) {
      name =
          contents.addChild(
              new EditBox(this.font, WIDTH, 20, Component.translatable("taerra.waypoint.name")));
      name.setMaxLength(MAX_NAME);
      name.setHint(Component.translatable("taerra.waypoint.name"));
      name.setValue(this.name);
      final @NotNull EditBox box = name;
      name.setResponder(
          value -> {
            final @NotNull String clean = NOT_NAME.matcher(value).replaceAll("");
            if (clean.equals(value)) {
              this.name = value;
            } else {
              box.setValue(clean);
            }
          });
    }
    final @NotNull LinearLayout editor = contents.addChild(LinearLayout.horizontal().spacing(6));
    final @NotNull Grid grid = editor.addChild(new Grid());
    grid.setTooltip(Tooltip.create(Component.translatable("taerra.icon_editor.hint")));
    final @NotNull LinearLayout side = editor.addChild(LinearLayout.vertical().spacing(4));
    side.addChild(new Previews());
    side.addChild(
        Button.builder(
                Component.translatable(
                    this.erasing ? "taerra.icon_editor.erase" : "taerra.icon_editor.draw"),
                _ -> {
                  this.erasing = !this.erasing;
                  rebuildWidgets();
                })
            .width(SIDE)
            .tooltip(Tooltip.create(Component.translatable("taerra.icon_editor.tool")))
            .build());
    final @NotNull LinearLayout history = side.addChild(LinearLayout.horizontal().spacing(2));
    this.undo =
        history.addChild(
            new IconButton(
                20, IconButton.UNDO, Component.translatable("taerra.selection.undo"), this::undo));
    this.undo.setTooltip(Shortcuts.undoTooltip());
    this.redo =
        history.addChild(
            new IconButton(
                20, IconButton.REDO, Component.translatable("taerra.selection.redo"), this::redo));
    this.redo.setTooltip(Shortcuts.redoTooltip());
    history.addChild(
        Button.builder(Component.translatable("taerra.icon_editor.clear"), _ -> clear())
            .width(SIDE - 44)
            .build());
    this.tint =
        side.addChild(
            new MultiLineTextWidget(Component.translatable("taerra.icon_editor.tint"), this.font)
                .setMaxWidth(SIDE));
    this.colors.addTo(contents, this.font);
    contents.addChild(new MultiLineTextWidget(this.error, this.font).setMaxWidth(WIDTH));

    final @NotNull Button cancel =
        Button.builder(
                this.armed
                    ? Component.translatable("taerra.settings.discard")
                        .withStyle(ChatFormatting.RED)
                    : Component.translatable("gui.cancel"),
                _ -> onClose())
            .width(150)
            .build();
    final @NotNull Button save =
        Button.builder(Component.translatable("taerra.waypoint.save"), _ -> save())
            .width(150)
            .build();
    save.active = !this.saving;
    this.scroll = new ScrollingContents(this.minecraft, this.layout, contents);
    this.footerWidth = this.width;
    FooterRows.add(
        this.layout,
        this.width,
        HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT,
        List.of(cancel, save));
    this.layout.visitWidgets(this::addRenderableWidget);
    repositionElements();
    tools();
    setInitialFocus();
  }

  @Override
  protected void repositionElements() {
    if (this.footerWidth != this.width) {
      rebuildWidgets();
      return;
    }
    if (this.scroll != null) {
      this.scroll.fit(this.layout);
    }
    this.layout.arrangeElements();
  }

  @Override
  public boolean keyPressed(final @NotNull KeyEvent event) {
    if (!(focusedLeaf() instanceof EditBox)
        && (Shortcuts.isUndo(event) || Shortcuts.isRedo(event))) {
      if (Shortcuts.isRedo(event)) {
        redo();
      } else {
        undo();
      }
      return true;
    }
    return super.keyPressed(event);
  }

  private @Nullable GuiEventListener focusedLeaf() {
    @Nullable GuiEventListener focused = getFocused();
    while (focused instanceof final ContainerEventHandler container
        && container.getFocused() != null) {
      focused = container.getFocused();
    }
    return focused;
  }

  private void tools() {
    if (this.undo != null && this.redo != null && this.tint != null) {
      this.undo.active = !this.history.isEmpty();
      this.redo.active = !this.future.isEmpty();
      this.tint.visible = Pixels.monochrome(this.pixels);
    }
  }

  private void begin(final boolean erase) {
    this.before = this.pixels.clone();
    this.stroke = erase ? 0 : 0xFF000000 | Colors.argb(this.color);
    this.last = -1;
  }

  private void paint(final int index) {
    if (this.before == null) {
      return;
    }
    this.last = index;
    this.pixels[index] = this.stroke;
  }

  private void line(final int to) {
    final int from = this.last < 0 ? to : this.last;
    final int x0 = from % Pixels.ICON;
    final int y0 = from / Pixels.ICON;
    final int x1 = to % Pixels.ICON;
    final int y1 = to / Pixels.ICON;
    final int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    for (int step = 1; step <= steps; step++) {
      paint(
          Math.round(y0 + (y1 - y0) * step / (float) steps) * Pixels.ICON
              + Math.round(x0 + (x1 - x0) * step / (float) steps));
    }
  }

  private void end() {
    if (this.before != null && !Arrays.equals(this.before, this.pixels)) {
      this.history.add(this.before);
      if (this.history.size() > HISTORY) {
        this.history.removeFirst();
      }
      this.future.clear();
    }
    this.before = null;
    tools();
  }

  private void clear() {
    begin(true);
    for (int i = 0; i < this.pixels.length; i++) {
      paint(i);
    }
    end();
  }

  private void undo() {
    step(this.history, this.future);
  }

  private void redo() {
    step(this.future, this.history);
  }

  private void step(final @NotNull List<int[]> from, final @NotNull List<int[]> to) {
    if (this.before != null || from.isEmpty()) {
      return;
    }
    to.add(this.pixels);
    this.pixels = from.removeLast();
    tools();
  }

  private void fail(final @NotNull Component error) {
    this.error = error.copy().withStyle(ChatFormatting.RED);
    this.saving = false;
    rebuildWidgets();
  }

  private void save() {
    final @NotNull String name = this.name.strip();
    if (this.icon == null && name.codePoints().noneMatch(Character::isLetterOrDigit)) {
      fail(Component.translatable("taerra.icons.bad_name"));
      return;
    }
    if (Arrays.stream(this.pixels).noneMatch(pixel -> pixel >>> 24 != 0)) {
      fail(Component.translatable("taerra.icon_editor.empty"));
      return;
    }
    final byte @NotNull [] png;
    try {
      png = Pixels.png(this.pixels);
    } catch (final IOException e) {
      fail(Component.translatable("taerra.icons.failed"));
      return;
    }
    this.saving = true;
    rebuildWidgets();
    if (this.icon == null) {
      this.map.uploadIcon(
          name,
          png,
          (status, _) ->
              done(
                  status,
                  Component.translatable("taerra.icons.uploaded", 1),
                  Component.literal(name)));
    } else {
      this.map.updateIcon(
          this.icon,
          null,
          png,
          status ->
              done(
                  status,
                  Component.translatable("taerra.icons.replaced", this.icon.displayName()),
                  this.icon.displayName()));
    }
  }

  private void done(
      final int status, final @NotNull Component message, final @NotNull Component name) {
    if (status / 100 != 2) {
      fail(Component.translatable(IconsScreen.problem(status), name, IconsScreen.MAX));
      return;
    }
    this.saved.accept(message);
    this.history.clear();
    onClose();
  }

  private final class Grid extends AbstractWidget {

    private int cursor;
    private boolean keyboard;

    private Grid() {
      super(
          0,
          0,
          Pixels.ICON * (CELL + 1) + 3,
          Pixels.ICON * (CELL + 1) + 3,
          Component.translatable("taerra.icon_editor.pixels"));
    }

    private int cellAt(final double x, final double y) {
      final int column = Math.clamp((int) ((x - getX() - 2) / (CELL + 1)), 0, Pixels.ICON - 1);
      final int row = Math.clamp((int) ((y - getY() - 2) / (CELL + 1)), 0, Pixels.ICON - 1);
      return row * Pixels.ICON + column;
    }

    @Override
    public void playDownSound(final @NotNull SoundManager sounds) {}

    @Override
    protected boolean isValidClickButton(final @NotNull MouseButtonInfo button) {
      return button.button() == InputConstants.MOUSE_BUTTON_LEFT
          || button.button() == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    @Override
    public void onClick(final @NotNull MouseButtonEvent event, final boolean doubleClick) {
      this.keyboard = false;
      begin(IconEditorScreen.this.erasing || event.button() == InputConstants.MOUSE_BUTTON_RIGHT);
      this.cursor = cellAt(event.x(), event.y());
      paint(this.cursor);
    }

    @Override
    protected void onDrag(final @NotNull MouseButtonEvent event, final double dx, final double dy) {
      this.cursor = cellAt(event.x(), event.y());
      line(this.cursor);
    }

    @Override
    public void onRelease(final @NotNull MouseButtonEvent event) {
      end();
    }

    @Override
    public boolean keyPressed(final @NotNull KeyEvent event) {
      this.keyboard = true;
      final int column = this.cursor % Pixels.ICON;
      final int row = this.cursor / Pixels.ICON;
      switch (event.key()) {
        case InputConstants.KEY_LEFT -> this.cursor = row * Pixels.ICON + Math.max(0, column - 1);
        case InputConstants.KEY_RIGHT ->
            this.cursor = row * Pixels.ICON + Math.min(Pixels.ICON - 1, column + 1);
        case InputConstants.KEY_UP -> this.cursor = Math.max(0, row - 1) * Pixels.ICON + column;
        case InputConstants.KEY_DOWN ->
            this.cursor = Math.min(Pixels.ICON - 1, row + 1) * Pixels.ICON + column;
        case InputConstants.KEY_SPACE, InputConstants.KEY_RETURN -> {
          begin(IconEditorScreen.this.erasing);
          paint(this.cursor);
          end();
        }
        default -> {
          return false;
        }
      }
      return true;
    }

    @Override
    protected void extractWidgetRenderState(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partialTick) {
      final int x = getX();
      final int y = getY();
      graphics.fill(
          x,
          y,
          x + getWidth(),
          y + getHeight(),
          isFocused() ? CommonColors.WHITE : CommonColors.LIGHT_GRAY);
      graphics.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, 0xFF3A3A3C);
      final int hovered = isHovered() ? cellAt(mouseX, mouseY) : -1;
      for (int i = 0; i < IconEditorScreen.this.pixels.length; i++) {
        final int left = x + 2 + i % Pixels.ICON * (CELL + 1);
        final int top = y + 2 + i / Pixels.ICON * (CELL + 1);
        graphics.fill(
            left,
            top,
            left + CELL,
            top + CELL,
            (i % Pixels.ICON + i / Pixels.ICON) % 2 == 0 ? 0xFF1E1E1F : 0xFF2A2A2C);
        final int pixel = IconEditorScreen.this.pixels[i];
        if (pixel >>> 24 != 0) {
          graphics.fill(left, top, left + CELL, top + CELL, pixel);
        }
        if (i == hovered || isFocused() && this.keyboard && !isHovered() && i == this.cursor) {
          graphics.outline(left - 1, top - 1, CELL + 2, CELL + 2, 0xFFFFFFFF);
        }
      }
    }

    @Override
    protected void updateWidgetNarration(final @NotNull NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, getMessage());
    }
  }

  private final class Previews extends AbstractWidget {

    private static final int SCALE = 2;
    private static final int BADGE = 24;

    private Previews() {
      super(0, 0, SIDE, 30, Component.empty());
      this.active = false;
    }

    @Override
    protected void extractWidgetRenderState(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partialTick) {
      final boolean tinted = Pixels.monochrome(IconEditorScreen.this.pixels);
      for (int i = 0; i < PREVIEWS.length; i++) {
        final MapIcons.@NotNull Badge badge =
            MapIcons.badge(IconEditorScreen.this.pixels, tinted, false, PREVIEWS[i]);
        final int left = getX() + i * (BADGE + 3);
        for (int p = 0; p < badge.argb().length; p++) {
          final int pixel = badge.argb()[p];
          if (pixel >>> 24 != 0) {
            final int px = left + p % badge.width() * SCALE;
            final int py = getY() + p / badge.width() * SCALE;
            graphics.fill(px, py, px + SCALE, py + SCALE, pixel);
          }
        }
      }
    }

    @Override
    protected void updateWidgetNarration(final @NotNull NarrationElementOutput output) {}
  }
}
