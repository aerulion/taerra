package net.aerulion.taerra.mod.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;

public abstract class PanelList<E extends ObjectSelectionList.Entry<E>>
    extends ObjectSelectionList<E> {

  public static final int WIDTH = 130;

  protected PanelList(final int width, final int rowHeight) {
    super(Minecraft.getInstance(), width, 0, 0, rowHeight);
  }

  @Override
  protected int scrollBarX() {
    return getRight() - 6;
  }

  @Override
  public int getRowWidth() {
    return this.width - 12;
  }
}
