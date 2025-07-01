import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;

public class ChatClientGUI extends JFrame {
    private static final String SERVER_ADDRESS = "192.168.1.10";
    private static final int SERVER_PORT = 12345;

    private Socket socket;
    private PrintWriter serverOut;
    private BufferedReader serverIn;

    private JTextArea chatArea;
    private JTextField messageField;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private String username;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }

    public ChatClientGUI() {
        setTitle("VARTALAAP");
        setSize(800, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
        showLoginWindow();
    }

    private void showLoginWindow() {
        JDialog loginDialog = new JDialog(this, "Login / Register", true);
        loginDialog.setSize(400, 300);
        loginDialog.setLayout(new GridBagLayout());
        loginDialog.getContentPane().setBackground(new Color(40, 40, 60));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel titleLabel = new JLabel("Welcome to VARTALAAP!");
        titleLabel.setFont(new Font("Segoe UI Emoji", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        loginDialog.add(titleLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        JLabel usernameLabel = new JLabel("Username:");
        usernameLabel.setForeground(Color.LIGHT_GRAY);
        loginDialog.add(usernameLabel, gbc);

        gbc.gridx = 1;
        JTextField usernameField = new JTextField(15);
        loginDialog.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setForeground(Color.LIGHT_GRAY);
        loginDialog.add(passwordLabel, gbc);

        gbc.gridx = 1;
        JPasswordField passwordField = new JPasswordField(15);
        loginDialog.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        JButton loginButton = createRoundedButton("Login", new Color(72, 133, 237));
        JButton registerButton = createRoundedButton("Register", new Color(76, 175, 80));
        loginDialog.add(loginButton, gbc);

        gbc.gridx = 1;
        loginDialog.add(registerButton, gbc);

        loginButton.addActionListener(e -> handleAuthentication(usernameField.getText(),
                new String(passwordField.getPassword()), "/login", loginDialog));
        registerButton.addActionListener(e -> handleAuthentication(usernameField.getText(),
                new String(passwordField.getPassword()), "/register", loginDialog));

        loginDialog.setLocationRelativeTo(null);
        loginDialog.setVisible(true);
    }

    private JButton createRoundedButton(String text, Color color) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                if (getModel().isArmed()) {
                    g.setColor(color.darker());
                } else if (getModel().isRollover()) {
                    g.setColor(color.brighter());
                } else {
                    g.setColor(color);
                }
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                super.paintComponent(g);
            }

            @Override
            protected void paintBorder(Graphics g) {
            }
        };
        button.setFont(new Font("Segoe UI Emoji", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        return button;
    }

    private void handleAuthentication(String username, String password, String command, JDialog loginDialog) {
        try {
            if (socket == null) {
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                serverOut = new PrintWriter(socket.getOutputStream(), true);
                serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            }

            serverOut.println(command + " " + username + " " + password);
            String response = serverIn.readLine();

            if (response.startsWith("Login successful") || response.startsWith("Registration successful")) {
                this.username = username;
                loginDialog.dispose();
                showChatWindow();
            } else {
                JOptionPane.showMessageDialog(this, response, "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to connect to server.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showChatWindow() {
        getContentPane().removeAll();
        setLayout(new BorderLayout());

        JLabel headerLabel = new JLabel("Welcome, " + username + "!", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Segoe UI Emoji", Font.BOLD, 22));
        headerLabel.setOpaque(true);
        headerLabel.setBackground(new Color(124, 69, 133));
        headerLabel.setForeground(Color.WHITE);
        headerLabel.setPreferredSize(new Dimension(100, 50));
        add(headerLabel, BorderLayout.NORTH);
        Color chatBackgroundColor = new Color(242, 239, 231);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        chatArea.setBackground(chatBackgroundColor);
        chatArea.setBorder(BorderFactory.createMatteBorder(0, 0, 28, 0, new Color(124, 69, 133)));
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(chatBackgroundColor);

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        messageField.addActionListener(e -> sendMessage());

        JButton sendButton = createRoundedButton("Send", new Color(96, 181, 255));
        JButton emojiButton = createRoundedButton("😊", new Color(255, 193, 7));
        JButton privateMessageButton = createRoundedButton("Private Chat", new Color(61, 54, 92));
        JButton fileButton = createRoundedButton("File", new Color(61, 54, 92));

        bottomPanel.add(messageField, BorderLayout.CENTER);
        JPanel sideButtons = new JPanel(new GridLayout(1, 0));
        sideButtons.setOpaque(false);
        sideButtons.add(emojiButton);
        sideButtons.add(fileButton);
        bottomPanel.add(sideButtons, BorderLayout.WEST);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.add(privateMessageButton, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(chatBackgroundColor);
        userList.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));

        sendButton.addActionListener(e -> sendMessage());
        emojiButton.addActionListener(e -> showEmojiPanel());
        privateMessageButton.addActionListener(e -> startPrivateChat());
        fileButton.addActionListener(e -> sendFile());

        revalidate();
        repaint();
        setVisible(true);
        new Thread(this::readMessages).start();
    }

    private void sendMessage() {
        String message = messageField.getText();
        if (!message.isEmpty()) {
            serverOut.println(message);
            messageField.setText("");
        }
    }

    private void showEmojiPanel() {
        JPopupMenu emojiMenu = new JPopupMenu();
        String[] emojis = { "😊", "😂", "❤", "👍", "😢", "😎", "🙌", "🎉", "😡", "😱" };

        for (String emoji : emojis) {
            JMenuItem emojiItem = new JMenuItem(emoji);
            emojiItem.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
            emojiItem.addActionListener(e -> {
                messageField.setText(messageField.getText() + emoji);
                messageField.requestFocusInWindow();
            });
            emojiMenu.add(emojiItem);
        }

        emojiMenu.show(this, messageField.getX(), messageField.getY() - emojiMenu.getPreferredSize().height);
    }

    private void readMessages() {
        try {
            String message;
            while ((message = serverIn.readLine()) != null) {
                if (message.startsWith("/users ")) {
                    updateOnlineUsers(message.substring(7));
                } else if (message.startsWith("/file ")) {
                    receiveFile(message);
                } else if (message.startsWith("/joined ")) {
                    String user = message.substring(8);
                    chatArea.append(user + " has joined the chat.\n");
                } else if (message.startsWith("/left ")) {
                    String user = message.substring(6);
                    chatArea.append(user + " has left the chat.\n");
                } else {
                    chatArea.append(message + "\n");
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Connection lost.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateOnlineUsers(String userListStr) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            String[] users = userListStr.split(",");
            Arrays.stream(users).map(String::trim).filter(name -> !name.isEmpty()).forEach(userListModel::addElement);
        });
    }

    private void startPrivateChat() {
        String recipient = JOptionPane.showInputDialog(this, "Enter the username of the recipient:");
        if (recipient != null && !recipient.isEmpty()) {
            String privateMessage = JOptionPane.showInputDialog(this, "Enter your private message:");
            if (privateMessage != null && !privateMessage.isEmpty()) {
                serverOut.println("/private " + recipient + " " + privateMessage);
            } else {
                JOptionPane.showMessageDialog(this, "Message cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Recipient username cannot be empty.", "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String encoded = Base64.getEncoder().encodeToString(fileBytes);
                serverOut.println("/file " + file.getName() + " " + encoded);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to send file.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void receiveFile(String message) {
        try {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3)
                return;

            String fileName = parts[1];
            byte[] fileBytes = Base64.getDecoder().decode(parts[2]);

            File downloadDir = new File(System.getProperty("user.home"), "Downloads");
            if (!downloadDir.exists())
                downloadDir.mkdirs();

            File outFile = new File(downloadDir, fileName);
            Files.write(outFile.toPath(), fileBytes);

            chatArea.append("File received and saved to: " + outFile.getAbsolutePath() + "\n");

            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().open(outFile);
                } catch (IOException ex) {
                    chatArea.append("Failed to open file: " + ex.getMessage() + "\n");
                }
            }
        } catch (Exception e) {
            chatArea.append("Failed to receive file.\n");
        }
    }
}