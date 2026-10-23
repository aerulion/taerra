package net.aerulion.taerra.mod.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.aerulion.taerra.mod.gui.widget.ColorField;
import net.aerulion.taerra.mod.gui.widget.FooterRows;
import net.aerulion.taerra.mod.gui.widget.IconChoice;
import net.aerulion.taerra.mod.gui.widget.ScrollingContents;
import net.aerulion.taerra.mod.map.MapIcons;
import net.aerulion.taerra.mod.map.Place;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Account;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.MapIcon;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
  private @Nullable ScrollingContents scroll;
  private final Screen parent;
  private final @NotNull WebMap map;
  private @NotNull Waypoint target;
  private final @NotNull Runnable saved;
  private final @NotNull ColorField colors;
  private String name;
  private String color;
  private String description;
  private @NotNull String icon;
  private String group;
  private @NotNull String newGroup = "";
  private boolean privateGroup;
  private final boolean ownGroup;
  private final boolean serverGroups;
  private List<WaypointGroup> groups = List.of();
  private @NotNull Component error = Component.empty();
  private boolean saving;
  private boolean armed;

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
    final @Nullable Account account = map.account();
    final @Nullable String color =
        target.previous() == null && account != null ? account.waypointColor() : target.color();
    this.color =
        color != null && Colors.isHex(color)
            ? color.toLowerCase(Locale.ROOT)
            : Waypoint.DEFAULT_COLOR;
    this.ownGroup = account != null && account.personal();
    this.serverGroups = account != null && account.markers();
    this.group = this.ownGroup && target.previous() == null ? Waypoint.PERSONAL : target.group();
    this.description = target.description();
    this.icon = target.points() == null && target.icon() != null ? target.icon() : "";
    this.colors = new ColorField(WIDTH, this.color, value -> this.color = value);
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
          .append(Component.literal(" " + MapIcons.PERSONAL).withStyle(ChatFormatting.GOLD));
    }
    final boolean restricted =
        this.groups.stream().anyMatch(group -> group.label().equals(value) && group.restricted());
    return restricted
        ? Component.literal(value)
            .append(Component.translatable("taerra.private.suffix").withStyle(ChatFormatting.GRAY))
        : Component.literal(value);
  }

  private @Nullable String groupIcon() {
    if (this.group.equals(NEW_GROUP)) {
      return null;
    }
    if (this.group.equals(Waypoint.PERSONAL) || this.group.isEmpty()) {
      final @NotNull String layer = this.group.isEmpty() ? "waypoints" : Layer.PERSONAL;
      return this.map.markers(this.target.world()).stream()
          .filter(known -> known.id().equals(layer))
          .map(Layer::icon)
          .filter(this.map::hasIcon)
          .findFirst()
          .orElse(null);
    }
    return this.groups.stream()
        .filter(known -> known.label().equals(this.group))
        .map(WaypointGroup::icon)
        .filter(this.map::hasIcon)
        .findFirst()
        .orElse(null);
  }

  private @Nullable Identifier texture(final @Nullable String icon) {
    return MapIcons.icon(this.map, icon, this.group.equals(Waypoint.PERSONAL));
  }

  private @NotNull Component fallbackLabel() {
    final @Nullable MapIcon icon = this.map.icon(groupIcon());
    return icon == null
        ? Component.translatable("taerra.icons.default")
        : Component.translatable("taerra.icons.group_icon", icon.displayName());
  }

  private void pickIcon() {
    this.minecraft.setScreenAndShow(
        new IconPickerScreen(
            this,
            this.map,
            Component.translatable("taerra.icons.choose"),
            this.icon,
            () -> texture(groupIcon()),
            fallbackLabel(),
            picked -> {
              this.icon = picked;
              rebuildWidgets();
            }));
  }

  @Override
  public void onClose() {
    this.minecraft.setScreenAndShow(this.parent);
  }

  @Override
  protected void init() {
    this.layout.removeChildren();
    this.layout.addTitleHeader(this.title, this.font);
    final @NotNull LinearLayout contents = LinearLayout.vertical().spacing(6);
    final @Nullable LocalPlayer player = this.minecraft.player;
    final boolean here =
        this.target.points() == null
            && player != null
            && this.target.world().equals(Dimensions.current());
    final @NotNull Component hereLabel = Component.translatable("taerra.waypoint.here");
    final int hereWidth = this.font.width(hereLabel) + 12;
    final @NotNull LinearLayout where = contents.addChild(LinearLayout.horizontal().spacing(4));
    where.addChild(
        new StringWidget(
            here ? WIDTH - hereWidth - 4 : WIDTH,
            here ? 20 : 9,
            (this.target.points() == null
                    ? new Place(this.target.x(), this.target.y(), this.target.z()).describe()
                    : Component.translatable("taerra.waypoint.points", this.target.points().size()))
                .copy()
                .withStyle(ChatFormatting.GRAY),
            this.font));
    if (here) {
      where.addChild(
          Button.builder(
                  hereLabel,
                  _ -> {
                    this.target =
                        this.target.at(player.getBlockX(), player.getBlockY(), player.getBlockZ());
                    rebuildWidgets();
                  })
              .width(hereWidth)
              .tooltip(Tooltip.create(Component.translatable("taerra.waypoint.here.hint")))
              .build());
    }
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
                .build(this.font, WIDTH, 40, Component.translatable("taerra.waypoint.description")),
            settings -> settings.paddingBottom(this.font.lineHeight));
    description.setCharacterLimit(MAX_DESCRIPTION);
    description.setValue(this.description);
    description.setValueListener(value -> this.description = value);

    this.colors.addTo(contents, this.font);
    if (this.target.points() == null) {
      final @Nullable MapIcon chosen = this.map.icon(this.icon);
      contents.addChild(
          new IconChoice(
              WIDTH,
              Component.translatable(
                  "taerra.waypoint.icon", chosen == null ? fallbackLabel() : chosen.displayName()),
              () -> texture(chosen == null ? groupIcon() : this.icon),
              this::pickIcon));
    }

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
    this.scroll = new ScrollingContents(this.minecraft, this.layout, contents);
    this.footerWidth = this.width;
    FooterRows.add(
        this.layout, this.width, HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT, buttons);
    this.layout.visitWidgets(this::addRenderableWidget);
    repositionElements();
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
        this.target.with(
            name,
            this.color,
            group,
            this.description.strip(),
            this.target.points() == null && !this.icon.isEmpty() ? this.icon : null),
        this.target.personal(),
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
