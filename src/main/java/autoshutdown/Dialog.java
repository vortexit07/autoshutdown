package autoshutdown;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.*;
import javax.swing.border.LineBorder;

public class Dialog extends JDialog {
    private JLabel messageLabel;
    private JTextField inputField;

    private Color backgroundColor = new Color(30, 30, 30);
    private Color fontColor = new Color(80, 81, 100);
    private Color highlightColor = fontColor;
    private Color accentColor = new Color(54, 51, 51);

    private int imgW = 50;
    private int imgH = 50;

    private static int result = 0;

    // Message dialog constructor
    public Dialog(JFrame parent, String message, String title, String type) {
        super(parent, title, true);
        setSize(380, 230);
        setLocationRelativeTo(parent);
        setResizable(false);
        setAlwaysOnTop(true);
        setFocusable(true);

        JLabel imageLabel = new JLabel();

        ImageIcon error = new ImageIcon(
                new ImageIcon(getClass().getClassLoader().getResource("assets/error.png"))
                        .getImage().getScaledInstance(imgW, imgH, Image.SCALE_SMOOTH));
        ImageIcon info = new ImageIcon(
                new ImageIcon(getClass().getClassLoader().getResource("assets/info.png"))
                        .getImage().getScaledInstance(imgW, imgH, Image.SCALE_SMOOTH));

        switch (type.toLowerCase()) {
            case "error":
                imageLabel.setIcon(error);
                break;

            case "info":
                imageLabel.setIcon(info);
        }

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBackground(backgroundColor);

        JButton closeButton = new JButton("Close");
        closeButton.setBackground(Color.GRAY);
        closeButton.setForeground(Color.WHITE);
        closeButton.setFocusPainted(false);
        closeButton.setBorder(new LineBorder(highlightColor, 2));
        closeButton.setFont(new Font("Dialog", Font.BOLD, 15));

        int buttonWidth = 120;
        int buttonHeight = 26;
        Dimension buttonSize = new Dimension(buttonWidth, buttonHeight);
        closeButton.setPreferredSize(buttonSize);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(45, -46, 0, 0);

        GridBagConstraints closeButtonConstraints = new GridBagConstraints();
        closeButtonConstraints.gridx = 0;
        closeButtonConstraints.gridy = 1;
        closeButtonConstraints.anchor = GridBagConstraints.CENTER;
        closeButtonConstraints.insets = new Insets(50, 0, -5, 0);

        GridBagConstraints imagegbc = new GridBagConstraints();
        imagegbc.gridx = 0;
        imagegbc.gridy = 0;
        imagegbc.weightx = 1.0;
        imagegbc.anchor = GridBagConstraints.WEST;
        imagegbc.insets = new Insets(32, 25, 0,10);

        messageLabel = new JLabel(message);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messageLabel.setBackground(backgroundColor);
        messageLabel.setForeground(Color.WHITE);
        messageLabel.setFont(new Font("Dialog", Font.PLAIN, 18));
        panel.add(messageLabel, gbc);

        addKeyListener((KeyListener) new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int key = e.getKeyCode();
                if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_ESCAPE) {
                    closeButton.doClick();
                }
            }
        });

        panel.add(closeButton, closeButtonConstraints);
        panel.add(imageLabel, imagegbc);

        closeButton.addActionListener(e -> dispose());
        add(panel);

    }

    // Input dialog constructor
    public Dialog(JFrame parent, String text) {
        super(parent, "Auto Shutdown", true);
        setSize(380, 250);
        setLocationRelativeTo(parent);
        setAlwaysOnTop(true);
        setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBackground(new Color(30, 30, 30));

        JLabel inputLabel = new JLabel(text);
        inputLabel.setForeground(Color.WHITE);
        inputLabel.setFont(new Font("Dialog", Font.PLAIN, 14));

        inputField = new JTextField(15);
        inputField.setBackground(accentColor);
        inputField.setForeground(Color.WHITE);
        inputField.setFont(new Font("Dialog", Font.BOLD, 15));
        inputField.setPreferredSize(new Dimension(200, 30));

        // Use CustomCaret instead of DefaultCaret
        CustomCaret customCaret = new CustomCaret();
        customCaret.setCaretWidth(2);
        inputField.setCaret(customCaret);
        inputField.setCaretColor(Color.WHITE);

        JButton okButton = new JButton("OK");
        // okButton.setBackground(new Color(30, 30, 30));
        okButton.setBackground(Color.GRAY);
        okButton.setForeground(Color.WHITE);
        okButton.setFocusPainted(false);

        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                okButton.doClick();
            }
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                inputField.setText("");
                dispose();
            }
        });

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(30, 10, 10, 10);

        GridBagConstraints inputConstraints = new GridBagConstraints();
        inputConstraints.gridx = 0;
        inputConstraints.gridy = 1;
        inputConstraints.fill = GridBagConstraints.HORIZONTAL; // Allow horizontal fill
        inputConstraints.anchor = GridBagConstraints.WEST;
        inputConstraints.insets = new Insets(15, 10, 5, 10); // Adjust top inset

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 2;
        buttonConstraints.anchor = GridBagConstraints.CENTER;
        buttonConstraints.insets = new Insets(10, 10, 0, 10); // Adjust top inset

        panel.add(inputLabel, labelConstraints);
        panel.add(inputField, inputConstraints);
        panel.add(okButton, buttonConstraints);

        add(panel);
    }

    // Confirmation dialog
    public Dialog(JFrame parent, String message, String title, String option1, String option2) {
        super(parent, title, true);
        setSize(380, 250);
        setLocationRelativeTo(parent);
        setResizable(false);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JLabel imageLabel = new JLabel();

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBackground(backgroundColor);

        ImageIcon confirm = new ImageIcon(
                new ImageIcon(getClass().getClassLoader().getResource("assets/warn.png"))
                        .getImage().getScaledInstance(imgW, imgH, Image.SCALE_SMOOTH));

        imageLabel.setIcon(confirm);

        GridBagConstraints imageConstraints = new GridBagConstraints();
        imageConstraints.gridx = 0;
        imageConstraints.gridy = 0;
        imageConstraints.weightx = 0.0;
        imageConstraints.anchor = GridBagConstraints.WEST;
        imageConstraints.insets = new Insets(30, 15, 0, 10);
        panel.add(imageLabel, imageConstraints);

        messageLabel = new JLabel(message);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messageLabel.setBackground(backgroundColor);
        messageLabel.setForeground(Color.WHITE);
        messageLabel.setFont(new Font("Dialog", Font.PLAIN, 18));

        GridBagConstraints messageConstraints = new GridBagConstraints();
        messageConstraints.gridx = 1;
        messageConstraints.gridy = 0;
        messageConstraints.gridwidth = GridBagConstraints.REMAINDER;
        messageConstraints.weightx = 1.0;
        messageConstraints.anchor = GridBagConstraints.WEST;
        messageConstraints.insets = new Insets(35, -10, 0, 10);
        panel.add(messageLabel, messageConstraints);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(Color.GRAY);
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setBorder(new LineBorder(highlightColor, 2));
        cancelButton.setFont(new Font("Dialog", Font.BOLD, 15));
        cancelButton.setPreferredSize(new Dimension(80, 25));

        JButton option1Button = new JButton(option1);
        option1Button.setBackground(Color.GRAY);
        option1Button.setForeground(Color.WHITE);
        option1Button.setFocusPainted(false);
        option1Button.setBorder(new LineBorder(highlightColor, 2));
        option1Button.setFont(new Font("Dialog", Font.BOLD, 15));
        option1Button.setPreferredSize(new Dimension(68, 25));

        JButton option2Button = new JButton(option2);
        option2Button.setBackground(Color.GRAY);
        option2Button.setForeground(Color.WHITE);
        option2Button.setFocusPainted(false);
        option2Button.setBorder(new LineBorder(highlightColor, 2));
        option2Button.setFont(new Font("Dialog", Font.BOLD, 15));
        option2Button.setPreferredSize(new Dimension(68, 25));

        cancelButton.addActionListener(e -> {
            result = 0;
            dispose();
        });

        option1Button.addActionListener(e -> {
            result = 1;
            dispose();
        });

        option2Button.addActionListener(e -> {
            result = 2;
            dispose();
        });

        // Cancel Button
        GridBagConstraints cancelButtonConstraints = new GridBagConstraints();
        cancelButtonConstraints.gridx = 0;
        cancelButtonConstraints.gridy = 2;
        cancelButtonConstraints.anchor = GridBagConstraints.SOUTHEAST;
        cancelButtonConstraints.weightx = 1.0;
        cancelButtonConstraints.insets = new Insets(50, 0, 10, -109);
        panel.add(cancelButton, cancelButtonConstraints);

        // Option 1 Button
        GridBagConstraints option1ButtonConstraints = new GridBagConstraints();
        option1ButtonConstraints.gridx = 1;
        option1ButtonConstraints.gridy = 2;
        option1ButtonConstraints.anchor = GridBagConstraints.SOUTHEAST;
        option1ButtonConstraints.weightx = 1.0;
        option1ButtonConstraints.insets = new Insets(50, 0, 10, -89);
        panel.add(option1Button, option1ButtonConstraints);

        // Option 2 Button
        GridBagConstraints option2ButtonConstraints = new GridBagConstraints();
        option2ButtonConstraints.gridx = 2;
        option2ButtonConstraints.gridy = 2;
        option2ButtonConstraints.anchor = GridBagConstraints.SOUTHEAST;
        option2ButtonConstraints.weightx = 1.0;
        option2ButtonConstraints.insets = new Insets(50, 0, 10, 20);
        panel.add(option2Button, option2ButtonConstraints);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(panel, BorderLayout.CENTER);

        setVisible(true);
    }

    public String getInput() {
        return inputField.getText();
    }

    public static void showMessageDialog(JFrame parent, String message, String title, String type) {
        Dialog dialog = new Dialog(parent, message, title, type);
        dialog.setVisible(true);
    }

    public static String showInputDialog(JFrame parent, String title) {
        Dialog dialog = new Dialog(parent, title);
        dialog.setVisible(true);
        return dialog.getInput();
    }

    public static int showConfirmDialog(JFrame parent, String message, String title, String option1, String option2) {
        new Dialog(parent, message, title, option1, option2);
        return result;
    }

}
