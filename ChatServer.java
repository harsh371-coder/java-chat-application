import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final int FILE_PORT = 12346;
    private static final String FILE_DIR = "received_files";
    private static final Map<String, ClientHandler> clientHandlers = new HashMap<>();
    private static final Map<String, String> users = new HashMap<>();
    private static final SimpleDateFormat timestampFormatter = new SimpleDateFormat("HH:mm:ss");

    public static void main(String[] args) {
        loadUsers();
        System.out.println("Chat server is running on port " + PORT);

        new Thread(ChatServer::startFileServer).start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected.");
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startFileServer() {
        File directory = new File(FILE_DIR);
        if (!directory.exists()) {
            directory.mkdir();
        }

        try (ServerSocket fileServerSocket = new ServerSocket(FILE_PORT)) {
            System.out.println("File server running on port " + FILE_PORT);
            while (true) {
                Socket fileSocket = fileServerSocket.accept();
                new Thread(() -> handleFileReceive(fileSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleFileReceive(Socket fileSocket) {
        try (DataInputStream dis = new DataInputStream(fileSocket.getInputStream())) {
            String sender = dis.readUTF();
            String recipient = dis.readUTF();
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();

            String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
            File outputFile = new File(FILE_DIR, uniqueFileName);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                int read;
                long remaining = fileSize;
                while ((read = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }

            String fileNotice = "[" + timestampFormatter.format(new Date()) + "] "
                    + sender + " sent file: " + fileName;

            if (recipient.equals("ALL")) {
                saveMessage("public_chat.txt", fileNotice);
                for (ClientHandler client : clientHandlers.values()) {
                    client.sendMessage(fileNotice + " (download: " + FILE_DIR + "/" + uniqueFileName + ")");
                    client.sendDownloadCommand(FILE_DIR + "/" + uniqueFileName);
                }
            } else {
                ClientHandler target = clientHandlers.get(recipient);
                if (target != null) {
                    target.sendMessage("[Private from " + sender + "] File received: " + fileName + " (downloaded)");
                    target.sendDownloadCommand(FILE_DIR + "/" + uniqueFileName);

                    ClientHandler senderHandler = clientHandlers.get(sender);
                    if (senderHandler != null) {
                        senderHandler.sendMessage("[Private to " + recipient + "] File sent: " + fileName);
                    }
                    saveMessage(getChatFileName(sender, recipient), fileNotice);
                }
            }

            System.out.println("File received: " + fileName + " from " + sender);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadUsers() {
        try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(":");
                if (data.length == 2) {
                    users.put(data[0], data[1]);
                }
            }
            System.out.println("User data loaded.");
        } catch (IOException e) {
            System.out.println("No user data found. Starting fresh.");
        }
    }

    private static void saveUsers() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("users.txt"))) {
            for (Map.Entry<String, String> entry : users.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void saveMessage(String filename, String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, true))) {
            writer.println(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void broadcastMessage(String sender, String message) {
        String timestamp = "[" + timestampFormatter.format(new Date()) + "] ";
        String formattedMessage = timestamp + sender + ": " + message;

        saveMessage("public_chat.txt", formattedMessage);
        for (ClientHandler client : clientHandlers.values()) {
            client.sendMessage(formattedMessage);
        }
    }

    private static synchronized void privateMessage(String sender, String recipient, String message) {
        ClientHandler recipientHandler = clientHandlers.get(recipient);
        String timestamp = "[" + timestampFormatter.format(new Date()) + "] ";
        String formattedMessage = timestamp + "[Private from " + sender + "]: " + message;
        String chatFileName = getChatFileName(sender, recipient);

        if (recipientHandler != null) {
            recipientHandler.sendMessage(formattedMessage);
            ClientHandler senderHandler = clientHandlers.get(sender);
            senderHandler.sendMessage(timestamp + "[Private to " + recipient + "]: " + message);
            saveMessage(chatFileName, formattedMessage);
        } else {
            ClientHandler senderHandler = clientHandlers.get(sender);
            senderHandler.sendMessage("User '" + recipient + "' not found or offline.");
        }
    }

    private static String getChatFileName(String user1, String user2) {
        List<String> sortedUsers = Arrays.asList(user1, user2);
        Collections.sort(sortedUsers);
        return sortedUsers.get(0) + "_" + sortedUsers.get(1) + ".txt";
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                if (!authenticate()) {
                    socket.close();
                    return;
                }

                sendUserStatus(username + " joined the chat.");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("/exit")) {
                        out.println("You have left the chat.");
                        break;
                    } else if (message.startsWith("/private")) {
                        String[] tokens = message.split(" ", 3);
                        if (tokens.length >= 3) {
                            String recipient = tokens[1];
                            String privateMessage = tokens[2];
                            privateMessage(username, recipient, privateMessage);
                        } else {
                            out.println("Invalid private message format. Use /private <username> <message>");
                        }
                    } else if (message.equalsIgnoreCase("/history")) {
                        displayChatHistory();
                    } else {
                        broadcastMessage(username, message);
                    }
                }
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            } finally {
                if (username != null) {
                    clientHandlers.remove(username);
                    sendUserStatus(username + " left the chat.");
                }
            }
        }

        private void displayChatHistory() {
            try {
                out.println("Public Chat History:");
                try (BufferedReader reader = new BufferedReader(new FileReader("public_chat.txt"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.println(line);
                    }
                }
                out.println("---- End of Public Chat History ----");

                out.println("Your Private Chat History:");
                for (String otherUser : users.keySet()) {
                    if (!otherUser.equals(username)) {
                        String chatFile = getChatFileName(username, otherUser);
                        File file = new File(chatFile);
                        if (file.exists()) {
                            out.println("Chat with " + otherUser + ":");
                            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    out.println(line);
                                }
                            }
                        }
                    }
                }
                out.println("---- End of Private Chat History ----");
            } catch (IOException e) {
                out.println("Error reading chat history.");
                e.printStackTrace();
            }
        }

        private boolean authenticate() throws IOException {
            while (true) {
                String[] credentials = in.readLine().split(" ", 3);

                if (credentials.length < 3) {
                    out.println("Invalid input. Please try again.");
                    continue;
                }

                String command = credentials[0];
                String usernameInput = credentials[1];
                String passwordInput = credentials[2];

                if (command.equalsIgnoreCase("/login")) {
                    if (users.containsKey(usernameInput) && users.get(usernameInput).equals(passwordInput)) {
                        this.username = usernameInput;
                        clientHandlers.put(username, this);
                        out.println("Login successful. Welcome, " + username + "!");
                        return true;
                    } else {
                        out.println("Invalid username or password.");
                    }
                } else if (command.equalsIgnoreCase("/register")) {
                    if (users.containsKey(usernameInput)) {
                        out.println("Username already exists.");
                    } else {
                        users.put(usernameInput, passwordInput);
                        saveUsers();
                        this.username = usernameInput;
                        clientHandlers.put(username, this);
                        out.println("Registration successful. Welcome, " + username + "!");
                        return true;
                    }
                } else {
                    out.println("Invalid command. Use /login or /register.");
                }
            }
        }

        private void sendMessage(String message) {
            out.println(message);
        }

        private void sendDownloadCommand(String filePath) {
            out.println("[DOWNLOAD]" + filePath);
        }

        private void sendUserStatus(String statusMessage) {
            for (ClientHandler client : clientHandlers.values()) {
                client.sendMessage(statusMessage);
            }
        }
    }
}