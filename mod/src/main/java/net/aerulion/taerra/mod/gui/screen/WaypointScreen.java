package net.aerulion.taerra.mod.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.aerulion.taerra.mod.gui.widget.ColorPicker;
import net.aerulion.taerra.mod.gui.widget.DyeSwatches;
import net.aerulion.taerra.mod.map.Place;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Account;
import net.aerulion.taerra.mod.web.model.Waypoint;
import net.aerulion.taerra.mod.web.model.WaypointGroup;
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
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaypointScreen extends Screen {

  private static final int MAX_NAME = 32;
  private static final int WIDTH = 200;
  private static final String NEW_GROUP = "\u0000new";

  private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private final Screen parent;
  private final @NotNull WebMap map;
  private final @NotNull Waypoint target;
  private final @NotNull ColorPicker picker;
  private String name;
  private String color;
  private String group;
  private @NotNull String newGroup = "";
  private boolean privateGroup;
  private List<WaypointGroup> groups = List.of();
  private @NotNull Component error = Component.empty();
  private boolean saving;
  private boolean armed;
  private @Nullable EditBox hex;

  public WaypointScreen(
      final Screen parent, final @NotNull WebMap map, final @NotNull Waypoint target) {
    super(
        Component.translatable(
            target.previous() == null ? "taerra.waypoint.new" : "taerra.waypoint.edit"));
    this.parent = parent;
    this.map = map;
    this.target = target;
    this.name = target.name();
    this.color =
        Colors.isHex(target.color())
            ? target.color().toLowerCase(Locale.ROOT)
            : Waypoint.DEFAULT_COLOR;
    this.group = target.group();
    this.picker = new ColorPicker(WIDTH, this.color, this::showColor);
    map.waypointGroups(
        loaded -> {
          this.groups = loaded;
          rebuildWidgets();
        });
  }

  private @NotNull Component groupName(final @NotNull String value) {
    if (value.isEmpty()) {
      return Component.translatable("taerra.waypoint.no_group");
    }
    if (value.equals(NEW_GROUP)) {
      return Component.translatable("taerra.waypoint.new_group");
    }
    final boolean restricted =
        this.groups.stream().anyMatch(group -> group.label().equals(value) && group.restricted());
    return restricted
        ? Component.literal(value)
            .append(Component.translatable("taerra.private.suffix").withStyle(ChatFormatting.GRAY))
        : Component.literal(value);
  }

  @Override
  public void onClose() {
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
            new Place(this.target.x(), this.target.y(), this.target.z())
                .describe()
                .copy()
                .withStyle(ChatFormatting.GRAY),
            this.font));
    final @NotNull EditBox name =
        contents.addChild(
            new EditBox(this.font, WIDTH, 20, Component.translatable("taerra.waypoint.name")));
    name.setMaxLength(MAX_NAME);
    name.setValue(this.name);
    name.setHint(Component.translatable("taerra.waypoint.name"));
    name.setResponder(value -> this.name = value);

    final @NotNull LinearLayout colors = contents.addChild(LinearLayout.horizontal().spacing(4));
    colors.addChild(new DyeSwatches(() -> this.color, this::pickColor));
    final @NotNull EditBox hex =
        colors.addChild(
            new EditBox(
                this.font,
                WIDTH - 4 - DyeColor.values().length * DyeSwatches.SWATCH / 2,
                20,
                Component.translatable("taerra.waypoint.hex")));
    hex.setMaxLength(7);
    hex.setValue(this.color);
    hex.setTextColor(Colors.argb(this.color));
    hex.setTooltip(Tooltip.create(Component.translatable("taerra.waypoint.hex")));
    hex.setResponder(
        value -> {
          final @NotNull String digits = value.startsWith("#") ? value.substring(1) : value;
          final @NotNull String typed = "#" + digits.toLowerCase(Locale.ROOT);
          if (Colors.isHex(typed) && !typed.equals(this.color)) {
            this.color = typed;
            this.picker.setHex(typed);
            hex.setTextColor(Colors.argb(typed));
          }
        });
    this.hex = hex;
    contents.addChild(this.picker);

    final @NotNull List<String> values = new ArrayList<>(List.of(""));
    this.groups.forEach(group -> values.add(group.label()));
    if (!values.contains(this.group) && !this.group.equals(NEW_GROUP)) {
      values.add(this.group);
    }
    values.add(NEW_GROUP);
    contents.addChild(
        CycleButton.builder(this::groupName, this.group)
            .withValues(values)
            .create(
                0,
                0,
                WIDTH,
                20,
                Component.translatable("taerra.waypoint.group"),
                (_, value) -> {
                  this.group = value;
                  rebuildWidgets();
                }));
    if (this.group.equals(NEW_GROUP)) {
      final @NotNull EditBox groupName =
          contents.addChild(
              new EditBox(
                  this.font, WIDTH, 20, Component.translatable("taerra.waypoint.group_name")));
      groupName.setMaxLength(MAX_NAME);
      groupName.setValue(this.newGroup);
      groupName.setHint(Component.translatable("taerra.waypoint.group_name"));
      groupName.setResponder(value -> this.newGroup = value);
      final @Nullable Account account = this.map.account();
      if (account != null && account.configure()) {
        contents.addChild(
            Checkbox.builder(Component.translatable("taerra.waypoint.private"), this.font)
                .selected(this.privateGroup)
                .tooltip(Tooltip.create(Component.translatable("taerra.waypoint.private.hint")))
                .onValueChange((_, value) -> this.privateGroup = value)
                .maxWidth(WIDTH)
                .build());
      }
    }
    contents.addChild(new StringWidget(WIDTH, 9, this.error, this.font));

    final @NotNull LinearLayout footer =
        this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
    final boolean editing = this.target.previous() != null;
    final int width = editing ? 98 : 150;
    if (editing) {
      footer.addChild(
                  Button.builder(
                          Component.translatable(
                                  this.armed
                                      ? "taerra.waypoint.confirm_delete"
                                      : "taerra.waypoint.delete")
                              .withStyle(ChatFormatting.RED),
                          _ -> delete())
                      .width(width)
                      .build())
              .active =
          !this.saving;
    }
    footer.addChild(
        Button.builder(Component.translatable("gui.cancel"), _ -> onClose()).width(width).build());
    footer.addChild(
                Button.builder(Component.translatable("taerra.waypoint.save"), _ -> save())
                    .width(width)
                    .build())
            .active =
        !this.saving;
    this.layout.visitWidgets(this::addRenderableWidget);
    repositionElements();
    setInitialFocus(name);
  }

  @Override
  protected void repositionElements() {
    this.layout.arrangeElements();
  }

  private void pickColor(final @NotNull String color) {
    this.picker.setHex(color);
    showColor(color);
  }

  private void showColor(final @NotNull String color) {
    this.color = color;
    if (this.hex != null) {
      this.hex.setValue(color);
      this.hex.setTextColor(Colors.argb(color));
    }
  }

  private void fail(final @NotNull Component error) {
    this.error = error.copy().withStyle(ChatFormatting.RED);
    this.saving = false;
    this.armed = false;
    rebuildWidgets();
  }

  private void save() {
    final @NotNull String name = this.name.strip();
    final boolean creating = this.group.equals(NEW_GROUP);
    final @NotNull String group = creating ? this.newGroup.strip() : this.group;
    if (name.isEmpty()) {
      fail(Component.translatable("taerra.waypoint.no_name"));
      return;
    }
    if (creating && group.isEmpty()) {
      fail(Component.translatable("taerra.waypoint.no_group_name"));
      return;
    }
    this.saving = true;
    rebuildWidgets();
    this.map.saveWaypoint(
        this.target.with(name, this.color, group),
        creating && this.privateGroup,
        status -> {
          if (status == 204) {
            onClose();
          } else {
            fail(
                Component.translatable(
                    status == 409 ? "taerra.waypoint.taken" : "taerra.waypoint.failed", name));
          }
        });
  }

  private void delete() {
    final @Nullable String previous = this.target.previous();
    if (previous == null) {
      return;
    }
    if (!this.armed) {
      this.armed = true;
      rebuildWidgets();
      return;
    }
    this.saving = true;
    rebuildWidgets();
    this.map.deleteWaypoint(
        this.target.world(),
        previous,
        status -> {
          if (status == 204) {
            onClose();
          } else {
            fail(Component.translatable("taerra.waypoint.failed", previous));
          }
        });
  }
}
