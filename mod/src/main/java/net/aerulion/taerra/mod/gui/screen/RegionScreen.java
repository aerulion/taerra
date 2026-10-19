package net.aerulion.taerra.mod.gui.screen;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.aerulion.taerra.mod.gui.widget.ColorField;
import net.aerulion.taerra.mod.gui.widget.FooterRows;
import net.aerulion.taerra.mod.web.WebMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionScreen extends Screen {

  private static final int WIDTH = 200;
  private static final int MAX_NAME = 48;

  private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private final @NotNull Screen parent;
  private final @NotNull WebMap map;
  private final @NotNull Draft draft;
  private final @NotNull Collection<Long> chunks;
  private final @NotNull Consumer<String> preview;
  private final @NotNull Listener listener;
  private final @NotNull String originalColor;
  private final @NotNull ColorField colors;
  private int footerWidth;
  private @NotNull String name;
  private @NotNull String fog;
  private boolean discoverable;
  private @NotNull Component error = Component.empty();
  private boolean saving;
  private boolean saved;
  private boolean deleteArmed;

  public RegionScreen(
      final @NotNull Screen parent,
      final @NotNull WebMap map,
      final @NotNull Draft draft,
      final @NotNull Collection<Long> chunks,
      final @NotNull Consumer<String> preview,
      final @NotNull Listener listener) {
    super(
        draft.id() == null
            ? Component.translatable("taerra.regions.new")
            : draft.name().isEmpty()
                ? Component.translatable("taerra.regions.unnamed")
                : Component.literal(draft.name()));
    this.parent = parent;
    this.map = map;
    this.draft = draft;
    this.chunks = chunks;
    this.preview = preview;
    this.listener = listener;
    this.name = draft.name();
    this.fog = draft.fog();
    this.discoverable = draft.discoverable();
    this.originalColor = draft.color();
    this.colors = new ColorField(WIDTH, draft.color(), preview);
  }

  public record Draft(
      @NotNull String world,
      @Nullable String id,
      @NotNull String name,
      @NotNull String color,
      @NotNull String fog,
      boolean discoverable) {}

  public interface Listener {

    void saved(@NotNull String id, @NotNull Draft draft, boolean rendering);

    void deleted();
  }

  @Override
  public void onClose() {
    if (!this.saved) {
      this.preview.accept(this.originalColor);
    }
    this.minecraft.setScreenAndShow(this.parent);
  }

  @Override
  protected void init() {
    this.layout.removeChildren();
    this.layout.addTitleHeader(this.title, this.font);
    final @NotNull LinearLayout contents =
        this.layout.addToContents(LinearLayout.vertical().spacing(6));
    contents.addChild(
        new StringWidget(
            WIDTH,
            9,
            meta(this.fog, this.chunks.size()).copy().withStyle(ChatFormatting.GRAY),
            this.font));
    final @NotNull EditBox name =
        contents.addChild(
            new EditBox(this.font, WIDTH, 20, Component.translatable("taerra.region.name")));
    name.setMaxLength(MAX_NAME);
    name.setValue(this.name);
    name.setHint(Component.translatable("taerra.region.name"));
    name.setResponder(value -> this.name = value);
    contents.addChild(
        CycleButton.<String>builder(
                fog -> Component.translatable("taerra.region.fog." + fog), this.fog)
            .withValues("hide", "veil", "none")
            .withTooltip(
                fog ->
                    Tooltip.create(Component.translatable("taerra.region.fog." + fog + ".tooltip")))
            .create(
                0,
                0,
                WIDTH,
                20,
                Component.translatable("taerra.region.fog"),
                (_, fog) -> {
                  this.fog = fog;
                  rebuildWidgets();
                }));
    if (!this.fog.equals("none")) {
      contents.addChild(
          Checkbox.builder(Component.translatable("taerra.region.discoverable"), this.font)
              .selected(this.discoverable)
              .tooltip(Tooltip.create(Component.translatable("taerra.region.discoverable.tooltip")))
              .onValueChange((_, value) -> this.discoverable = value)
              .maxWidth(WIDTH)
              .build());
    }
    this.colors.addTo(contents, this.font);
    contents.addChild(new StringWidget(WIDTH, 9, this.error, this.font));

    final boolean editing = this.draft.id() != null;
    final int width = editing ? 98 : 150;
    final @NotNull List<Button> buttons = new ArrayList<>();
    if (editing) {
      final @NotNull Button delete =
          Button.builder(
                  Component.translatable(
                          this.deleteArmed
                              ? "taerra.waypoint.confirm_delete"
                              : "taerra.waypoint.delete")
                      .withStyle(ChatFormatting.RED),
                  _ -> delete())
              .width(width)
              .build();
      delete.active = !this.saving;
      buttons.add(delete);
    }
    buttons.add(
        Button.builder(Component.translatable("gui.cancel"), _ -> onClose()).width(width).build());
    final @NotNull Button save =
        Button.builder(Component.translatable("taerra.region.save"), _ -> save())
            .width(width)
            .build();
    save.active = !this.saving;
    buttons.add(save);
    this.footerWidth = this.width;
    FooterRows.add(
        this.layout, this.width, HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT, buttons);
    this.layout.visitWidgets(this::addRenderableWidget);
    repositionElements();
    setInitialFocus(name);
  }

  @Override
  protected void repositionElements() {
    if (this.footerWidth != this.width) {
      rebuildWidgets();
      return;
    }
    this.layout.arrangeElements();
  }

  public static @NotNull Component meta(final @NotNull String fog, final int size) {
    return Component.translatable("taerra.region.fog." + fog)
        .append(", ")
        .append(Component.translatable("taerra.regions.size", size));
  }

  public static @NotNull String problem(final int status, final @Nullable String body) {
    try {
      if (body != null) {
        return "taerra.region.problem."
            + JsonParser.parseString(body).getAsJsonObject().get("problem").getAsString();
      }
    } catch (final RuntimeException ignored) {
    }
    return status == 409 ? "taerra.region.problem.busy" : "taerra.region.problem.failed";
  }

  private void fail(final @NotNull Component error) {
    this.error = error.copy().withStyle(ChatFormatting.RED);
    this.saving = false;
    this.deleteArmed = false;
    rebuildWidgets();
  }

  private void save() {
    this.saving = true;
    rebuildWidgets();
    final @NotNull Draft draft =
        new Draft(
            this.draft.world(),
            this.draft.id(),
            this.name.strip(),
            this.colors.color(),
            this.fog,
            !this.fog.equals("none") && this.discoverable);
    this.map.saveRegion(
        draft.world(),
        draft.id(),
        draft.name(),
        draft.color(),
        draft.fog(),
        draft.discoverable(),
        this.chunks,
        (status, body) -> {
          if (status == 200 || status == 202) {
            final @Nullable String id = id(body) == null ? draft.id() : id(body);
            this.saved = true;
            onClose();
            if (id != null) {
              this.listener.saved(id, draft, status == 202);
            }
          } else {
            fail(Component.translatable(problem(status, body)));
          }
        });
  }

  private static @Nullable String id(final @Nullable String body) {
    try {
      return body == null
          ? null
          : JsonParser.parseString(body).getAsJsonObject().get("id").getAsString();
    } catch (final RuntimeException e) {
      return null;
    }
  }

  private void delete() {
    final @Nullable String id = this.draft.id();
    if (id == null) {
      return;
    }
    if (!this.deleteArmed) {
      this.deleteArmed = true;
      rebuildWidgets();
      return;
    }
    this.saving = true;
    rebuildWidgets();
    this.map.deleteRegion(
        id,
        (status, body) -> {
          if (status == 200 || status == 202) {
            this.saved = true;
            onClose();
            this.listener.deleted();
          } else {
            fail(Component.translatable(problem(status, body)));
          }
        });
  }
}
