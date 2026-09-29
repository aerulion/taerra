package net.aerulion.taerra.rendering.color;

import net.aerulion.taerra.rendering.block.BlockShape;

public record BlockRenderData(
    int topRgb, int sideRgb, BlockShape shape, boolean tintTop, boolean tintSide) {}
