package org.takesome.kaylasEngine.gui.components.dropBox;

public interface DropBoxListener {

    void onScrollBoxCreated(DropBox dropBox);
    void onScrollBoxOpen(DropBox dropBox);
    void onScrollBoxClose(DropBox dropBox);
    void onServerHover(DropBox dropBox, int hover);
}
