package org.takesome.kaylasEngine.gui.components.combobox;

public interface ComboboxListener {
    void onComboboxCreated(Combobox combobox);

    void onComboboxOpen(Combobox combobox);

    void onComboboxClose(Combobox combobox);

    void onComboboxHover(Combobox combobox, int hoverIndex);
}
