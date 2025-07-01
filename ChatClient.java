import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_ADDRESS = "192.168.1.10";
    private static final int SERVER_PORT = 12345;
    private Socket socket;
    private BufferedReader serverIn;
    private PrintWriter serverOut;
    private String username;

    public ChatClient() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            serverOut = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("Connected to the chat server.");

            if (!authenticate()) {
                System.out.println("Authentication failed. Exiting...");
                socket.close();
                return;
            }

            new Thread(this::readMessages).start();
            sendMessages();
        } catch (IOException e) {
            System.out.println("Error connecting to server: " + e.getMessage());
        }
    }

    private boolean authenticate() throws IOException {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Do you want to login or register? (login/register): ");
            String action = scanner.nextLine().trim().toLowerCase();

            System.out.print("Enter username: ");
            username = scanner.nextLine().trim();

            System.out.print("Enter password: ");
            String password = scanner.nextLine().trim();

            serverOut.println("/" + action + " " + username + " " + password);

            String response = serverIn.readLine();
            System.out.println(response);

            if (response.startsWith("Login successful") || response.startsWith("Registration successful")) {
                return true;
            }
        }
    }

    private void readMessages() {
        try {
            String message;
            while ((message = serverIn.readLine()) != null) {
                System.out.println(message);
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server.");
        }
    }

    private void sendMessages() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("You can now start chatting. Type '/exit' to leave.");

        while (true) {
            String message = scanner.nextLine();

            if (message.equalsIgnoreCase("/exit")) {
                serverOut.println("/exit");
                break;
            } else if (message.equalsIgnoreCase("/history")) {
                serverOut.println("/history");
            } else if (message.startsWith("/private")) {

                if (message.split(" ", 3).length < 3) {
                    System.out.println("Invalid private message format. Use: /private <username> <message>");
                } else {
                    serverOut.println(message);
                }
            } else if (message.trim().isEmpty()) {
                System.out.println("Message cannot be empty.");
            } else {
                serverOut.println(message);
            }
        }
        System.out.println("You have left the chat.");
    }

    public static void main(String[] args) {
        new ChatClient();
    }
}