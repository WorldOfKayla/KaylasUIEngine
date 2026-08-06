package org.takesome.kaylasEngine.gui.components.textfield;

import org.takesome.kaylasEngine.gui.animation.AnimationEngine;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicTextFieldUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.Serial;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

public class TextField extends JTextField {
	@Serial
	private static final long serialVersionUID = 1L;
	private TextFieldListener textFieldListener;
	private int carretDelay = 500;
	public BufferedImage texture;
	private int paddingX = 0;
	private int paddingY = 0;
	private boolean caretVisible = true;
	private boolean selected = false;
	private Color selectionColor;
	private Color selectedTextColor = Color.white;
	private AnimationEngine.Handle caretAnimation;
	private final String placeholder;
	private boolean maskingEnabled;
	private char maskCharacter = '*';

	public TextField(ComponentFactory componentFactory) {
		this.placeholder = componentFactory.getEngine().getLANG().getString(componentFactory.getComponentAttribute().getLocaleKey());
		this.selectionColor = hexToColor(componentFactory.getStyle().getSelectionColor());
		setOpaque(false);
		setText(this.placeholder);

		addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				if (getText().equals(placeholder)) {
					setText("");
					repaint();
					revalidate();
				}
				startCaretBlinking();
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (getText().isEmpty()) {
					setText(placeholder);
				}
				stopCaretBlinking();
			}
		});

		// Enable caret movement by clicking
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				super.mousePressed(e);
				selected = false;
				setCaretPosition(viewToModel(e.getPoint()));
			}
		});

		// Track selection of text
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				super.mouseDragged(e);
				selected = true;
			}
		});

		// Add key listener for arrow key handling (move caret left and right)
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				// Move caret left
				if (e.getKeyCode() == KeyEvent.VK_LEFT) {
					if (getCaretPosition() > 0) {
						setCaretPosition(getCaretPosition() - 1);
					}
				}
				// Move caret right
				else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
					if (getCaretPosition() < getText().length()) {
						setCaretPosition(getCaretPosition() + 1);
					}
				}
			}
		});

		// Set up text field UI and custom caret painting
		this.setUI(new BasicTextFieldUI());
	}

	private void startCaretBlinking() {
		if (caretAnimation != null && caretAnimation.isActive()) {
			return;
		}
		caretVisible = true;
		caretAnimation = AnimationEngine.shared().interval(carretDelay, carretDelay, () -> {
			if (!isFocusOwner() || !isDisplayable()) {
				caretAnimation = null;
				return false;
			}
			caretVisible = !caretVisible;
			repaint();
			return true;
		});
	}

	private void stopCaretBlinking() {
		if (caretAnimation != null) {
			caretAnimation.cancel();
			caretAnimation = null;
		}
		caretVisible = true;
		repaint();
	}

	@Override
	public void removeNotify() {
		stopCaretBlinking();
		super.removeNotify();
	}

	@Override
	protected void paintComponent(Graphics maing) {
		Graphics2D g = (Graphics2D) maing.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Draw background texture if exists
		if (texture != null) {
			g.drawImage(texture, 0, 0, getWidth(), getHeight(), null);
		}

		// Draw the text
		g.setColor(getForeground());
		int x = paddingX;
		int y = paddingY + g.getFontMetrics().getAscent();

		String sourceText = getText();
		String displayText = toDisplayText(sourceText);
		if (displayText != null) {
			g.drawString(displayText, x, y);
		}

		// Draw the caret only when visible and the text field has focus
		if (isFocusOwner() && caretVisible) {
			try {
				int caretPosition = Math.max(0, Math.min(getCaretPosition(), displayText.length()));
				int caretX = x + g.getFontMetrics().stringWidth(displayText.substring(0, caretPosition));
				int caretY = y - g.getFontMetrics().getAscent();
				g.drawLine(caretX, caretY, caretX, caretY + g.getFontMetrics().getHeight());
			} catch (StringIndexOutOfBoundsException ignored) {}
		}

		// Draw selection highlight
		if (selected) {
			int start = Math.min(getSelectionStart(), getSelectionEnd());
			int end = Math.max(getSelectionStart(), getSelectionEnd());
			g.setColor(selectionColor);

			int safeStart = Math.max(0, Math.min(start, displayText.length()));
			int safeEnd = Math.max(safeStart, Math.min(end, displayText.length()));
			if (safeStart < safeEnd) {
				String selectedText = displayText.substring(safeStart, safeEnd);
				int selStart = x + g.getFontMetrics().stringWidth(displayText.substring(0, safeStart));
				int selEnd = x + g.getFontMetrics().stringWidth(displayText.substring(0, safeEnd));
				g.fillRect(selStart, y - g.getFontMetrics().getAscent(), selEnd - selStart, g.getFontMetrics().getHeight());
				g.setColor(selectedTextColor);
				g.drawString(selectedText, selStart, y);
			}
		}

		g.dispose();
	}

	public void setTextFieldListener(TextFieldListener textFieldListener) {
		this.textFieldListener = textFieldListener;
		getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				checkText();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				checkText();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				checkText();
			}

			private void checkText() {
				if (!getText().equals(placeholder)) {
					textFieldListener.onTextChange(TextField.this);
				}
			}
		});
	}

	private String toDisplayText(String sourceText) {
		if (sourceText == null || !maskingEnabled || sourceText.equals(placeholder)) {
			return sourceText == null ? "" : sourceText;
		}
		return String.valueOf(maskCharacter).repeat(sourceText.length());
	}

	public void setMaskingEnabled(boolean maskingEnabled) {
		this.maskingEnabled = maskingEnabled;
		repaint();
	}

	public boolean isMaskingEnabled() {
		return maskingEnabled;
	}

	public void setMaskCharacter(char maskCharacter) {
		if (Character.isISOControl(maskCharacter)) {
			throw new IllegalArgumentException("maskCharacter must be printable");
		}
		this.maskCharacter = maskCharacter;
		repaint();
	}

	public char getMaskCharacter() {
		return maskCharacter;
	}

	public String getDisplayText() {
		return toDisplayText(getText());
	}

	public void setPaddingX(int paddingX) {
		this.paddingX = paddingX;
	}

	public void setPaddingY(int paddingY) {
		this.paddingY = paddingY;
	}

	public void setSelectionColor(Color selectionColor) {
		this.selectionColor = selectionColor;
	}

	public void setSelectedTextColor(Color selectedTextColor) {
		this.selectedTextColor = selectedTextColor;
	}

	public Color getSelectionColor() {
		return selectionColor;
	}

	public String getValue() {
		String text = getText();
		return text == null || text.equals(placeholder) ? "" : text;
	}

	public Color getSelectedTextColor() {
		return selectedTextColor;
	}

	public void resetText() {
		setText(placeholder);
		repaint();
		revalidate();
	}

	// Helper method to view mouse click position and adjust caret
	public int viewToModel(Point pt) {
		return viewToModel2D(pt);
	}
}
