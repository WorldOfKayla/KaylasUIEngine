package org.takesome.kaylasEngine.gui.components.dropBox;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.combobox.Combobox;
import org.takesome.kaylasEngine.gui.components.combobox.ComboboxListener;
import org.takesome.kaylasEngine.gui.components.combobox.ComboboxState;

/**
 * @deprecated Use {@link Combobox}. Kept as a source-compatible adapter for older launcher layouts.
 */
@Deprecated(since = "1.18.0-AURELIA", forRemoval = false)
@SuppressWarnings("unused")
public class DropBox extends Combobox {
    private DropBoxListener dropBoxListener;

    public DropBox(ComponentFactory componentFactory, String[] values, int initialY) {
        super(componentFactory, values, initialY);
    }

    public DropBox(ComponentFactory componentFactory, int initialY) {
        super(componentFactory, initialY);
    }

    public void setScrollBoxListener(DropBoxListener dropBoxListener) {
        this.dropBoxListener = dropBoxListener;
        if (dropBoxListener == null) {
            super.setComboboxListener(null);
            return;
        }
        super.setComboboxListener(new ComboboxListener() {
            @Override
            public void onComboboxCreated(Combobox combobox) {
                DropBox.this.dropBoxListener.onScrollBoxCreated(DropBox.this);
            }

            @Override
            public void onComboboxOpen(Combobox combobox) {
                DropBox.this.dropBoxListener.onScrollBoxOpen(DropBox.this);
            }

            @Override
            public void onComboboxClose(Combobox combobox) {
                DropBox.this.dropBoxListener.onScrollBoxClose(DropBox.this);
            }

            @Override
            public void onComboboxHover(Combobox combobox, int hoverIndex) {
                DropBox.this.dropBoxListener.onServerHover(DropBox.this, hoverIndex);
            }
        });
    }

    public State getState() {
        ComboboxState state = getComboboxState();
        return switch (state) {
            case OPENED -> State.OPENED;
            case ROLLOVER -> State.ROLLOVER;
            default -> State.CLOSED;
        };
    }
}
