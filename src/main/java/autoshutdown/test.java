package autoshutdown;

import javax.swing.*;
import javax.swing.border.Border;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class test {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Rounded Button Example");
        frame.setSize(300, 200);
        frame.setLayout(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JButton roundedButton = new JButton("Rounded Button");
        roundedButton.setPreferredSize(new Dimension(150, 50));
        
        // Set a rounded border for the button directly
        roundedButton.setBorder(new RoundedBorder(10)); // 10 is the radius for rounded corners
        roundedButton.setBounds(100, 100, 200, 200);
        
        frame.getContentPane().add(roundedButton, BorderLayout.CENTER);
        frame.setVisible(true);
    }
}

// Custom RoundedBorder class implementing Border
class RoundedBorder implements Border {
    private int radius;

    RoundedBorder(int radius) {
        this.radius = radius;
    }

    public Insets getBorderInsets(Component c) {
        return new Insets(this.radius + 1, this.radius + 1, this.radius + 2, this.radius);
    }

    public boolean isBorderOpaque() {
        return true;
    }

    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(Color.BLACK); // Set border color
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.draw(new RoundRectangle2D.Double(x, y, width - 1, height - 1, radius, radius));
    }
}
