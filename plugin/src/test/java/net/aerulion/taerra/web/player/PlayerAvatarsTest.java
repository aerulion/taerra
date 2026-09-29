package net.aerulion.taerra.web.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerAvatarsTest {

  @Test
  @DisplayName("the face is the skin's front head with the hat layer drawn over it")
  void faceCompositesHatOverHead() {
    final @NotNull BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int y = 8; y < 16; y++) {
      for (int x = 8; x < 16; x++) {
        skin.setRGB(x, y, 0xFF112233);
      }
    }
    skin.setRGB(40, 8, 0xFFAABBCC);

    final @NotNull BufferedImage face = PlayerAvatars.face(skin);

    assertEquals(0xFFAABBCC, face.getRGB(0, 0));
    assertEquals(0xFF112233, face.getRGB(7, 7));
  }

  @Test
  @DisplayName("a legacy 64x32 skin with a fully opaque hat region shows no hat")
  void legacyOpaqueHatIsIgnored() {
    final @NotNull BufferedImage skin = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 32; y++) {
      for (int x = 0; x < 64; x++) {
        skin.setRGB(x, y, x >= 32 ? 0xFF000000 : 0x00112233);
      }
    }

    final @NotNull BufferedImage face = PlayerAvatars.face(skin);

    assertEquals(0xFF112233, face.getRGB(3, 3));
  }

  @Test
  @DisplayName("a legacy 64x32 skin with any transparency in the hat region keeps its hat")
  void legacyTransparentHatIsKept() {
    final @NotNull BufferedImage skin = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
    skin.setRGB(40, 8, 0xFFAABBCC);

    assertEquals(0xFFAABBCC, PlayerAvatars.face(skin).getRGB(0, 0));
  }
}
