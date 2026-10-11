package net.aerulion.taerra.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.ResourceBundle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessagesTest {

  private static @NotNull String render(
      final @NotNull Component component, final @NotNull Locale locale) {
    return PlainTextComponentSerializer.plainText()
        .serialize(GlobalTranslator.render(component, locale));
  }

  @Test
  @DisplayName("every language has exactly the same messages")
  void languagesMatch() {
    final ResourceBundle.Control control =
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    assertEquals(
        ResourceBundle.getBundle("lang.taerra", Locale.ENGLISH, control).keySet(),
        ResourceBundle.getBundle("lang.taerra", Locale.GERMAN, control).keySet());
  }

  @Test
  @DisplayName("players see messages in their client language, falling back to English")
  void translatesPerLocale() {
    final @NotNull Translator translations = Messages.registerTranslations();
    try {
      final @NotNull Component message = Messages.tr("waypoint.unknown", Messages.value("Home"));
      assertEquals("Es gibt keinen Wegpunkt namens Home.", render(message, Locale.GERMANY));
      assertEquals("No waypoint called Home.", render(message, Locale.FRANCE));
      assertEquals(
          "Home isn't on the map. Turn it on with /taerra first.",
          render(
              Messages.tr(
                  "render.world_disabled", Messages.value("Home"), Messages.value("/taerra")),
              Locale.US));
      assertEquals(
          java.util.List.of("Last Death", "Letzter Tod"),
          Messages.plainInEveryLocale("taerra.waypoint.death"));
    } finally {
      GlobalTranslator.translator().removeSource(translations);
    }
  }
}
