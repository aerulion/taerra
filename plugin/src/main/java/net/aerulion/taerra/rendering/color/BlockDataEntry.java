package net.aerulion.taerra.rendering.color;

import com.fasterxml.jackson.annotation.JsonProperty;

record BlockDataEntry(
    String top,
    String side,
    String shape,
    @JsonProperty("tint_top") Boolean tintTop,
    @JsonProperty("tint_side") Boolean tintSide) {}
