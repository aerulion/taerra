package net.aerulion.taerra.mod.util;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.NotNull;

public record Texture(GpuTexture texture, GpuTextureView view) implements AutoCloseable {

  public static @NotNull Texture upload(final String label, final @NotNull NativeImage image) {
    try (image) {
      final @NotNull GpuDevice device = RenderSystem.getDevice();
      final @NotNull GpuTexture texture =
          device.createTexture(
              () -> label,
              GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
              GpuFormat.RGBA8_UNORM,
              image.getWidth(),
              image.getHeight(),
              1,
              1);
      final @NotNull GpuTextureView view = device.createTextureView(texture);
      device.createCommandEncoder().writeToTexture(texture, image);
      return new Texture(texture, view);
    }
  }

  @Override
  public void close() {
    this.view.close();
    this.texture.close();
  }
}
