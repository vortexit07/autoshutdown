package autoshutdown;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Rectangle;

public class CustomCaret extends DefaultCaret implements ActionListener {
    private int caretWidth = 3;
    private boolean isVisible = true;
    private Timer blinkTimer;

    public CustomCaret() {
        blinkTimer = new Timer(500, this);
        blinkTimer.start();
    }

    @Override
    protected synchronized void damage(Rectangle r) {
        if (r == null) return;
        int caretWidth = this.getCaretWidth();
        x = r.x - (caretWidth >> 1) + 2;
        y = r.y;
        width = caretWidth;
        height = r.height;
        repaint();
    }

    @Override
    public void paint(java.awt.Graphics g) {
        if (!isVisible) return; // If not visible, don't paint

        JTextComponent component = getComponent();
        if (component == null) return;

        int dot = getDot();
        Rectangle caret;
        try {
            caret = component.modelToView2D(dot).getBounds();
            if (caret == null) return;
        } catch (javax.swing.text.BadLocationException e) {
            return;
        }

        if ((x != caret.x) || (y != caret.y)) {
            repaint(); // Erase previous location of caret
            x = caret.x - (caretWidth >> 1) + 2;
            y = caret.y;
            width = caretWidth;
            height = caret.height;
        }

        if (component.isShowing()) {
            g.setColor(getComponent().getCaretColor());
            g.fillRect(x, y, width, height);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        isVisible = !isVisible; // Toggle visibility on each timer tick
        repaint(); // Repaint the caret to show or hide it
    }

    public int getCaretWidth() {
        return caretWidth;
    }

    public void setCaretWidth(int caretWidth) {
        this.caretWidth = caretWidth;
    }
}
