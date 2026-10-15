package net.aerulion.taerra.mod.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.aerulion.taerra.mod.gui.widget.ColorPicker;
import net.aerulion.taerra.mod.gui.widget.DyeSwatches;
import net.aerulion.taerra.mod.gui.widget.FooterRows;
import net.aerulion.taerra.mod.map.MapIcons;
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
import net.minecraft.client.gui.components.MultiLineEditBox;
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
  private static final int MAX_DESCRIPTION = 280;
  private static final Pattern NOT_NAME = Pattern.compile("[^\\p{L}\\p{N} _.,:'&()#!?+-]");
  private static final int WIDTH = 200;
  private static final String NEW_GROUP = "\u0000new";

  private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private int footerWidth;
  private final Screen parent;
  private final @NotNull WebMap map;
  private final @NotNull Waypoint target;
  private final @NotNull Runnable saved;
  private final @NotNull ColorPicker picker;
  private String name;
  private String color;
  private String description;
  private String group;
  private @NotNull String newGroup = "";
  private boolean privateGroup;
  private final boolean ownGroup;
  private final boolean serverGroups;
  private List<WaypointGroup> groups = List.of();
  private @NotNull Component error = Component.empty();
  private boolean saving;
  private boolean armed;
  private @Nullable EditBox hex;

  private static void cleanName(final @NotNull EditBox box, final @NotNull Consumer<String> then) {
    box.setResponder(
        value -> {
          final @NotNull String clean = NOT_NAME.matcher(value).replaceAll("");
          if (clean.equals(value)) {
            then.accept(value);
          } else {
            box.setValue(clean);
          }
        });
  }

  public WaypointScreen(
      final Screen parent,
      final @NotNull WebMap map,
      final @NotNull Waypoint target,
      final @NotNull Runnable saved) {
    super(
        Component.translatable(
            "taerra."
                + (target.points() == null ? "waypoint" : target.type())
                + (target.previous() == null ? ".new" : ".edit")));
    this.parent = parent;
    this.map = map;
    this.target = target;
    this.saved = saved;
    this.name = target.name();
    this.color =
        Colors.isHex(target.color())
            ? target.color().toLowerCase(Locale.ROOT)
            : Waypoint.DEFAULT_COLOR;
    final @Nullable Account account = map.account();
    this.ownGroup =
        account != null && account.personal() && (target.personal() || target.previous() == null);
    this.serverGroups = account != null && account.markers() && !target.personal();
    this.group = this.ownGroup && target.previous() == null ? Waypoint.PERSONAL : target.group();
    this.description = target.description();
    this.picker = new ColorPicker(WIDTH, this.color, this::showColor);
    if (this.serverGroups) {
      map.waypointGroups(
          loaded -> {
            this.groups = loaded;
            rebuildWidgets();
          });
    }
  }

  private @NotNull Component groupName(final @NotNull String value) {
    if (value.isEmpty()) {
      return Component.translatable("taerra.waypoint.no_group");
    }
    if (value.equals(NEW_GROUP)) {
      return Component.translatable("taerra.waypoint.new_group");
    }
    if (value.equals(Waypoint.PERSONAL)) {
      return Component.translatable("taerra.waypoint.personal")
          .append(Component.literal(" " + MapIcons.GEM).withStyle(ChatFormatting.GOLD));
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
            (this.target.points() == null
                    ? new Place(this.target.x(), this.target.y(), this.target.z()).describe()
                    : Component.translatable("taerra.waypoint.points", this.target.points().size()))
                .copy()
                .withStyle(ChatFormatting.GRAY),
            this.font));
    final @NotNull EditBox name =
        contents.addChild(
            new EditBox(this.font, WIDTH, 20, Component.translatable("taerra.waypoint.name")));
    name.setMaxLength(MAX_NAME);
    cleanName(name, value -> this.name = value);
    name.setValue(this.name);
    name.setHint(Component.translatable("taerra.waypoint.name"));
    final @NotNull MultiLineEditBox description =
        contents.addChild(
            MultiLineEditBox.builder()
                .setPlaceholder(
                    Component.translatable("taerra.waypoint.description.hint")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .build(
                    this.font, WIDTH, 40, Component.translatable("taerra.waypoint.description")));
    description.setCharacterLimit(MAX_DESCRIPTION);
    description.setValue(this.description);
    description.setValueListener(value -> this.description = value);

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

    final @NotNull List<String> values = new ArrayList<>();
    if (this.ownGroup) {
      values.add(Waypoint.PERSONAL);
    }
    if (this.serverGroups) {
      values.add("");
      this.groups.forEach(group -> values.add(group.label()));
      if (!values.contains(this.group) && !this.group.equals(NEW_GROUP)) {
        values.add(this.group);
      }
      values.add(NEW_GROUP);
    }
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
      cleanName(groupName, value -> this.newGroup = value);
      groupName.setValue(this.newGroup);
      groupName.setHint(Component.translatable("taerra.waypoint.group_name"));
      final @Nullable Account account = this.map.account();
      if (account != null && account.privateGroups()) {
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

    final boolean editing = this.target.previous() != null;
    final int width = editing ? 98 : 150;
    final @NotNull List<Button> buttons = new ArrayList<>();
    if (editing) {
      final @NotNull Button delete =
          Button.builder(
                  Component.translatable(
                          this.armed ? "taerra.waypoint.confirm_delete" : "taerra.waypoint.delete")
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
        Button.builder(Component.translatable("taerra.waypoint.save"), _ -> save())
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
    if (name.codePoints().noneMatch(Character::isLetterOrDigit)) {
      fail(Component.translatable("taerra.waypoint.no_name"));
      return;
    }
    if (creating && group.codePoints().noneMatch(Character::isLetterOrDigit)) {
      fail(Component.translatable("taerra.waypoint.no_group_name"));
      return;
    }
    this.saving = true;
    rebuildWidgets();
    this.map.saveWaypoint(
        this.target.with(name, this.color, group, this.description.strip()),
        creating && this.privateGroup,
        status -> {
          if (status == 204) {
            onClose();
            this.saved.run();
          } else {
            fail(
                Component.translatable(
                    switch (status) {
                      case 409 -> "taerra.waypoint.taken";
                      case 422 -> "taerra.waypoint.full";
                      default -> "taerra.waypoint.failed";
                    },
                    name));
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
        this.target.personal(),
        status -> {
          if (status == 204) {
            onClose();
            this.saved.run();
          } else {
            fail(Component.translatable("taerra.waypoint.failed", previous));
          }
        });
  }
}
