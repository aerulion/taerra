package net.aerulion.taerra.command;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.NotNull;

public final class Messages {

  static final @NotNull TextColor BRAND = TextColor.color(0xd4732f);
  static final @NotNull TextColor TEXT = NamedTextColor.GRAY;
  static final @NotNull TextColor VALUE = NamedTextColor.WHITE;
  static final @NotNull TextColor MUTED = TextColor.color(0x8c8d90);
  static final @NotNull TextColor DIM = TextColor.color(0x58585a);
  static final @NotNull TextColor GREEN = TextColor.color(0x52a535);
  static final @NotNull TextColor GOLD = TextColor.color(0xffcf3f);
  static final @NotNull TextColor RED = TextColor.color(0xe34a4a);

  private static final @NotNull List<Locale> LOCALES = List.of(Locale.ENGLISH, Locale.GERMAN);
  private static final int BAR_WIDTH = 20;
  private static final @NotNull Component SEPARATOR = Component.text(" ◦ ", DIM);

  private Messages() {
    super();
  }

  public static @NotNull Translator registerTranslations() {
    final TranslationStore.@NotNull StringBased<MessageFormat> store =
        TranslationStore.messageFormat(Key.key("taerra", "messages"));
    store.defaultLocale(Locale.ENGLISH);
    for (final @NotNull Locale locale : LOCALES) {
      store.registerAll(
          locale,
          ResourceBundle.getBundle(
              "lang.taerra",
              locale,
              Messages.class.getClassLoader(),
              ResourceBundle.Control.getNoFallbackControl(
                  ResourceBundle.Control.FORMAT_PROPERTIES)),
          true);
    }
    GlobalTranslator.translator().addSource(store);
    return store;
  }

  public static @NotNull String plain(final @NotNull String key, final @NotNull Locale locale) {
    return PlainTextComponentSerializer.plainText()
        .serialize(GlobalTranslator.render(Component.translatable(key), locale));
  }

  public static @NotNull List<String> plainInEveryLocale(final @NotNull String key) {
    return LOCALES.stream().map(locale -> plain(key, locale)).toList();
  }

  public static @NotNull Component renderSuggestion(
      final @NotNull String worldName, final int chunks) {
    return info(
        "render.suggest_full",
        world(worldName),
        tr("unit.chunks", value(chunks)),
        command("/taerra render full " + worldName));
  }

  public static @NotNull Component webTeleport(
      final @NotNull String coordinates, final @NotNull String worldName) {
    return success("teleport.done", copyable(coordinates), world(worldName));
  }

  public static @NotNull Component webLogin() {
    return warning("login.done", command("/taerra logout"));
  }

  public static @NotNull Component joinHint(final @NotNull String mapUrl) {
    final @NotNull Component mod = link("https://download.taerra.dev", tr("join_hint.mod"));
    return mapUrl.isEmpty()
        ? info("join_hint.mod_only", mod)
        : info("join_hint.text", link(mapUrl, tr("login.open")), mod);
  }

  static @NotNull TranslatableComponent tr(
      final @NotNull String key, final @NotNull ComponentLike... args) {
    return Component.translatable("taerra." + key, args);
  }

  static @NotNull Component success(
      final @NotNull String key, final @NotNull ComponentLike... args) {
    return line("✔", GREEN, key, args);
  }

  static @NotNull Component info(final @NotNull String key, final @NotNull ComponentLike... args) {
    return line("›", BRAND, key, args);
  }

  static @NotNull Component warning(
      final @NotNull String key, final @NotNull ComponentLike... args) {
    return line("⚠", GOLD, key, args);
  }

  static @NotNull Component error(final @NotNull String key, final @NotNull ComponentLike... args) {
    return line("✖", RED, key, args);
  }

  private static @NotNull Component line(
      final @NotNull String icon,
      final @NotNull TextColor color,
      final @NotNull String key,
      final @NotNull ComponentLike... args) {
    return Component.text()
        .append(Component.text(icon + " ", color))
        .append(tr(key, args).color(TEXT))
        .build();
  }

  static @NotNull Component value(final @NotNull Object value) {
    return Component.text(String.valueOf(value), VALUE);
  }

  static @NotNull Component world(final @NotNull String worldName) {
    return Component.text(worldName, GREEN);
  }

  static @NotNull Component copyable(final @NotNull String text) {
    return Component.text(text, VALUE)
        .hoverEvent(tr("click.copy").color(TEXT))
        .clickEvent(ClickEvent.copyToClipboard(text));
  }

  static @NotNull Component link(final @NotNull String url, final @NotNull ComponentLike label) {
    return Component.text()
        .color(GOLD)
        .append(label)
        .hoverEvent(tr("click.open").color(TEXT))
        .clickEvent(ClickEvent.openUrl(url))
        .build();
  }

  static @NotNull Component command(final @NotNull String command) {
    return Component.text(command, GOLD)
        .hoverEvent(tr("click.run", Component.text(command, GOLD)).color(TEXT))
        .clickEvent(ClickEvent.runCommand(command));
  }

  static @NotNull Component button(
      final @NotNull String command, final @NotNull ComponentLike label) {
    return Component.text()
        .color(GOLD)
        .append(Component.text("["))
        .append(label)
        .append(Component.text("]"))
        .hoverEvent(tr("click.run", Component.text(command, GOLD)).color(TEXT))
        .clickEvent(ClickEvent.runCommand(command))
        .build();
  }

  static @NotNull Component suggest(
      final @NotNull String command, final @NotNull ComponentLike hover) {
    return Component.text(command, GOLD)
        .hoverEvent(
            Component.join(
                JoinConfiguration.newlines(),
                Component.text().color(TEXT).append(hover).build(),
                tr("click.suggest").color(MUTED)))
        .clickEvent(ClickEvent.suggestCommand(command + " "));
  }

  static @NotNull Component header(final @NotNull ComponentLike title) {
    return Component.text()
        .append(Component.text("TAERRA", BRAND, TextDecoration.BOLD))
        .append(SEPARATOR)
        .append(Component.text().color(VALUE).append(title))
        .build();
  }

  static @NotNull Component section(final @NotNull String key) {
    return tr(key).color(GOLD);
  }

  static @NotNull Component row(final @NotNull String labelKey, final ComponentLike... parts) {
    return Component.text()
        .append(Component.text("  "))
        .append(tr(labelKey).color(MUTED))
        .append(Component.text("  "))
        .append(Component.text().color(TEXT).append(parts(parts)))
        .build();
  }

  static @NotNull Component indent(final @NotNull ComponentLike... parts) {
    return Component.text()
        .append(Component.text("  "))
        .append(Component.text().color(TEXT).append(parts(parts)))
        .build();
  }

  static @NotNull Component parts(final @NotNull ComponentLike... parts) {
    return Component.join(JoinConfiguration.separator(SEPARATOR), parts);
  }

  static @NotNull Component bar(final double fraction) {
    final int filled = RenderReport.filled(fraction, BAR_WIDTH);
    return Component.text()
        .append(Component.text("━".repeat(filled), GREEN))
        .append(Component.text("━".repeat(BAR_WIDTH - filled), DIM))
        .build();
  }

  static @NotNull Component lines(final @NotNull List<? extends ComponentLike> lines) {
    return Component.join(JoinConfiguration.newlines(), lines);
  }
}
