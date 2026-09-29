package net.aerulion.taerra.storage;

import java.nio.file.Path;

public record WorldImageExport(Path file, int width, int height, int tiles, long bytes) {}
