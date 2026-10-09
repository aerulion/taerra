package net.aerulion.taerra.mod.util;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

public final class FileDialogs {

  private FileDialogs() {}

  public static void open(final @NotNull Consumer<@Nullable Path> then) {
    show(false, null, then);
  }

  public static void save(
      final @NotNull Path suggested, final @NotNull Consumer<@Nullable Path> then) {
    show(true, suggested.toString(), then);
  }

  private static void show(
      final boolean save,
      final @Nullable String location,
      final @NotNull Consumer<@Nullable Path> then) {
    final @NotNull ByteBuffer name = MemoryUtil.memUTF8("JSON");
    final @NotNull ByteBuffer pattern = MemoryUtil.memUTF8("json");
    final @Nullable ByteBuffer where = location == null ? null : MemoryUtil.memUTF8(location);
    final SDL_DialogFileFilter.@NotNull Buffer filters = SDL_DialogFileFilter.calloc(1);
    filters.get(0).name(name).pattern(pattern);
    final SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
    callback[0] =
        SDL_DialogFileCallback.create(
            (userdata, files, filter) -> {
              final long first =
                  files == MemoryUtil.NULL ? MemoryUtil.NULL : MemoryUtil.memGetAddress(files);
              final @Nullable String chosen =
                  first == MemoryUtil.NULL ? null : MemoryUtil.memUTF8(first);
              Minecraft.getInstance()
                  .execute(
                      () -> {
                        MemoryUtil.memFree(name);
                        MemoryUtil.memFree(pattern);
                        MemoryUtil.memFree(where);
                        filters.free();
                        callback[0].free();
                        then.accept(chosen == null ? null : Path.of(chosen));
                      });
            });
    final long window = Minecraft.getInstance().getWindow().handle();
    if (save) {
      SDLDialog.SDL_ShowSaveFileDialog(callback[0], MemoryUtil.NULL, window, filters, where);
    } else {
      SDLDialog.SDL_ShowOpenFileDialog(callback[0], MemoryUtil.NULL, window, filters, where, false);
    }
  }
}
