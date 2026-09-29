package net.aerulion.taerra.mod.map;

record Look(int fill, int hoverFill, int ground, int wall, int wallLine) {

  static final Look COLOR = new Look(0x1F, 0x57, 0x14, 0x29, 0x59);
  static final Look GRAY = new Look(0x38, 0x73, 0x29, 0x47, 0x99);
}
