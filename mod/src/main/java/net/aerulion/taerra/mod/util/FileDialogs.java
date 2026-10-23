package net.aerulion.taerra.mod.util;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

public final class FileDialogs {

  private FileDialogs() {}

  public static void open(final @NotNull Consumer<@Nullable Path> then) {
    show(
        false,
        "JSON",
        "json",
        false,
        null,
        paths -> then.accept(paths.isEmpty() ? null : paths.getFirst()));
  }

  public static void openImages(final @NotNull Consumer<List<Path>> then) {
    show(false, "PNG", "png", true, null, then);
  }

  public static void save(
      final @NotNull Path suggested, final @NotNull Consumer<@Nullable Path> then) {
    show(
        true,
        "JSON",
        "json",
        false,
        suggested.toString(),
        paths -> then.accept(paths.isEmpty() ? null : paths.getFirst()));
  }

  private static void show(
      final boolean save,
      final @NotNull String kind,
      final @NotNull String extension,
      final boolean many,
      final @Nullable String location,
      final @NotNull Consumer<List<Path>> then) {
    final @NotNull ByteBuffer name = MemoryUtil.memUTF8(kind);
    final @NotNull ByteBuffer pattern = MemoryUtil.memUTF8(extension);
    final @Nullable ByteBuffer where = location == null ? null : MemoryUtil.memUTF8(location);
    final SDL_DialogFileFilter.@NotNull Buffer filters = SDL_DialogFileFilter.calloc(1);
    filters.get(0).name(name).pattern(pattern);
    final SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
    callback[0] =
        SDL_DialogFileCallback.create(
            (userdata, files, filter) -> {
              final @NotNull List<Path> chosen = new ArrayList<>();
              for (long at = files;
                  files != MemoryUtil.NULL && MemoryUtil.memGetAddress(at) != MemoryUtil.NULL;
                  at += Pointer.POINTER_SIZE) {
                chosen.add(Path.of(MemoryUtil.memUTF8(MemoryUtil.memGetAddress(at))));
              }
              Minecraft.getInstance()
                  .execute(
                      () -> {
                        MemoryUtil.memFree(name);
                        MemoryUtil.memFree(pattern);
                        MemoryUtil.memFree(where);
                        filters.free();
                        callback[0].free();
                        then.accept(chosen);
                      });
            });
    final long window = Minecraft.getInstance().getWindow().handle();
    if (save) {
      SDLDialog.SDL_ShowSaveFileDialog(callback[0], MemoryUtil.NULL, window, filters, where);
    } else {
      SDLDialog.SDL_ShowOpenFileDialog(callback[0], MemoryUtil.NULL, window, filters, where, many);
    }
  }
}
