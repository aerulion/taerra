package net.aerulion.taerra.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JoinHintTest {

  private static final long DAY = TimeUnit.DAYS.toMillis(1);

  @Test
  @DisplayName("the hint shows on a first join and again once the interval has passed")
  void due() {
    final long now = 100 * DAY;
    assertTrue(JoinHint.due(0L, now, 7));
    assertFalse(JoinHint.due(now - 6 * DAY, now, 7));
    assertTrue(JoinHint.due(now - 7 * DAY, now, 7));
    assertFalse(JoinHint.due(0L, now, 0));
  }
}
