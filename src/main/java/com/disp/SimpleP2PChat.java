package com.disp;

import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.*;

public class SimpleP2PChat {
    // =========================================================================
    // КОНСТАНТЫ
    // =========================================================================
    private static final String SIGNAL_SERVER_HOST = "localhost";
    private static final int SIGNAL_SERVER_PORT = 8888;
    private static final int FILE_CHUNK_SIZE = 1024; // Размер чанка для файлов
    private static final String RECEIVED_FILES_DIR = "./received_files/"; // Папка для сохранения файлов

    // =========================================================================
    // ПОЛЯ КЛАССА
    // =========================================================================
    private DatagramSocket socket;
    private InetAddress partnerAddress;
    private int partnerPort;
    private String partnerName;
    private String userName;
    private boolean running = true;
    private boolean partnerConnected = false;

    private List<PeerInfo> availablePeers = new CopyOnWriteArrayList<>();

    // Для передачи файлов
    private FileOutputStream fileOutputStream = null;
    private String receivingFileName = null;
    private long receivingFileSize = 0;
    private long receivedBytesCount = 0;

    // =========================================================================
    // CALLBACK ИНТЕРФЕЙСЫ ДЛЯ GUI
    // =========================================================================
    public interface MessageCallback {
        void onMessageReceived(String sender, String message);
    }

    public interface StatusCallback {
        void onStatusChanged(boolean connected, String partnerName);
    }

    public interface PeersCallback {
        void onPeersUpdated(List<PeerInfo> peers);
    }

    public interface FileProgressCallback {
        void onFileProgress(String fileName, int progress, boolean isSending);
    }

    private MessageCallback messageCallback = null;
    private StatusCallback statusCallback = null;
    private PeersCallback peersCallback = null;
    private FileProgressCallback fileProgressCallback = null;

    // =========================================================================
    // КОНСТРУКТОР
    // =========================================================================
    public SimpleP2PChat(int port, String userName) throws SocketException {
        this.userName = userName;
        this.socket = new DatagramSocket(port);

        // Создаем папку для полученных файлов
        createReceivedFilesDirectory();

        System.out.println("=== Чат запущен ===");
        System.out.println("Твой адрес: localhost:" + port);
        System.out.println("Твой ник: " + userName);
        System.out.println("Папка для файлов: " + RECEIVED_FILES_DIR);
        System.out.println("------------------------");

        registerWithSignalServer(port);
    }

    // =========================================================================
    // МЕТОДЫ УСТАНОВКИ CALLBACK
    // =========================================================================
    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    public void setStatusCallback(StatusCallback callback) {
        this.statusCallback = callback;
    }

    public void setPeersCallback(PeersCallback callback) {
        this.peersCallback = callback;
    }

    public void setFileProgressCallback(FileProgressCallback callback) {
        this.fileProgressCallback = callback;
    }

    // =========================================================================
    // МЕТОДЫ РАБОТЫ С ФАЙЛАМИ
    // =========================================================================
    private void createReceivedFilesDirectory() {
        File dir = new File(RECEIVED_FILES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    // =========================================================================
    // МЕТОДЫ РАБОТЫ С СИГНАЛЬНЫМ СЕРВЕРОМ
    // =========================================================================
    private void registerWithSignalServer(int chatPort) {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();
            String registerMsg = "REGISTER|" + userName + "|" + chatPort;
            String response = sendToSignalServer(registerMsg);

            if (response != null && response.startsWith("REGISTERED")) {
                System.out.println("✓ Зарегистрирован на сигнальном сервере");
                startHeartbeat();
            } else {
                System.out.println("✗ Ошибка регистрации на сигнальном сервере");
            }
        } catch (Exception e) {
            System.out.println("✗ Не удалось подключиться к сигнальному серверу");
        }
    }

    private void startHeartbeat() {
        new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(30000);
                    sendToSignalServer("HEARTBEAT|" + userName);
                } catch (Exception e) {
                    // Игнорируем ошибки heartbeat
                }
            }
        }).start();
    }

    private String sendToSignalServer(String message) throws Exception {
        try (DatagramSocket tempSocket = new DatagramSocket()) {
            tempSocket.setSoTimeout(3000);

            InetAddress serverAddr = InetAddress.getByName(SIGNAL_SERVER_HOST);
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, serverAddr, SIGNAL_SERVER_PORT
            );
            tempSocket.send(packet);

            byte[] buffer = new byte[4096];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            tempSocket.receive(response);

            return new String(response.getData(), 0, response.getLength());
        }
    }

    // =========================================================================
    // МЕТОДЫ РАБОТЫ СО СПИСКОМ ПИРОВ
    // =========================================================================
    public void refreshPeersList() {
        try {
            String response = sendToSignalServer("GET_PEERS");
            parsePeersList(response);
            displayPeersList();

            if (peersCallback != null) {
                peersCallback.onPeersUpdated(availablePeers);
            }
        } catch (Exception e) {
            System.out.println("Ошибка получения списка пиров: " + e.getMessage());
        }
    }

    private void parsePeersList(String response) {
        availablePeers.clear();
        if (response != null && response.startsWith("PEERS|")) {
            String peersData = response.substring(6);
            if (!peersData.isEmpty()) {
                String[] peers = peersData.split(",");
                for (String peer : peers) {
                    String[] parts = peer.split(":");
                    if (parts.length == 3 && !parts[0].equals(userName)) {
                        availablePeers.add(new PeerInfo(
                                parts[0], parts[1], Integer.parseInt(parts[2])
                        ));
                    }
                }
            }
        }
    }

    private void displayPeersList() {
        if (availablePeers.isEmpty()) {
            System.out.println("📋 Нет доступных пиров");
        } else {
            System.out.println("\n📋 Доступные пиры:");
            for (int i = 0; i < availablePeers.size(); i++) {
                System.out.println("   " + i + ": " + availablePeers.get(i));
            }
            System.out.println("------------------------");
        }
    }

    public List<PeerInfo> getAvailablePeers() {
        return availablePeers;
    }

    // =========================================================================
    // МЕТОДЫ ПОДКЛЮЧЕНИЯ К ПИРАМ
    // =========================================================================
    public void connectToPeer(PeerInfo peer) {
        try {
            partnerAddress = InetAddress.getByName(peer.getIp());
            partnerPort = peer.getPort();
            partnerName = peer.getName();
            partnerConnected = true;

            String helloMsg = "*** " + userName + " подключился к чату ***";
            sendMessage(helloMsg);

            String nameMsg = "NAME|" + userName;
            sendMessage(nameMsg);

            System.out.println("✓ Подключение к " + peer + " установлено!");

            if (statusCallback != null) {
                statusCallback.onStatusChanged(true, partnerName);
            }
        } catch (Exception e) {
            System.out.println("✗ Ошибка подключения к пиру: " + e.getMessage());
            partnerConnected = false;
            partnerName = null;
        }
    }

    public void connectToPeer(int index) {
        if (index >= 0 && index < availablePeers.size()) {
            connectToPeer(availablePeers.get(index));
        } else {
            System.out.println("✗ Неверный номер пира");
        }
    }

    public void disconnectFromPeer() {
        if (partnerConnected) {
            sendMessage("*** " + userName + " покинул чат ***");
            partnerAddress = null;
            partnerPort = 0;
            partnerName = null;
            partnerConnected = false;
            System.out.println("✓ Отключен от собеседника");

            if (statusCallback != null) {
                statusCallback.onStatusChanged(false, null);
            }
        }
    }

    public boolean isConnected() {
        return partnerConnected;
    }

    public String getUserName() {
        return userName;
    }

    // =========================================================================
    // МЕТОДЫ ОТПРАВКИ СООБЩЕНИЙ
    // =========================================================================
    public void sendMessage(String message) {
        if (partnerAddress != null && partnerConnected) {
            try {
                // Шифруем сообщение
                String encrypted = SimpleCryptoXOR.encrypt(message);
                byte[] data = encrypted.getBytes("UTF-8");

                DatagramPacket packet = new DatagramPacket(
                        data, data.length, partnerAddress, partnerPort
                );
                socket.send(packet);
            } catch (IOException e) {
                System.out.println("Ошибка отправки: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // МЕТОДЫ ОТПРАВКИ ФАЙЛОВ
    // =========================================================================
    public void sendFile(String filePath) {
        if (!partnerConnected) {
            System.out.println("✗ Нет подключения к собеседнику");
            if (fileProgressCallback != null) {
                fileProgressCallback.onFileProgress("", 0, true);
            }
            return;
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.out.println("✗ Файл не найден: " + filePath);
            return;
        }

        // Запускаем отправку в отдельном потоке
        new Thread(() -> {
            try {
                long fileSize = file.length();
                System.out.println("📤 Начинаем отправку файла: " + file.getName() +
                        " (" + fileSize + " байт)");

                // Читаем файл в массив байтов
                byte[] fileBytes = Files.readAllBytes(file.toPath());

                // Отправляем метаданные файла
                String metaMsg = "FILE_META|" + file.getName() + "|" + fileSize;
                sendMessage(metaMsg);

                // Небольшая задержка
                Thread.sleep(100);

                // Отправляем файл частями
                int totalChunks = (int) Math.ceil((double) fileBytes.length / FILE_CHUNK_SIZE);
                int bytesSent = 0;

                for (int i = 0; i < totalChunks; i++) {
                    int start = i * FILE_CHUNK_SIZE;
                    int end = Math.min(start + FILE_CHUNK_SIZE, fileBytes.length);
                    int chunkSize = end - start;

                    byte[] chunk = Arrays.copyOfRange(fileBytes, start, end);
                    String chunkBase64 = Base64.getEncoder().encodeToString(chunk);

                    String dataMsg = "FILE_DATA|" + i + "|" + chunkBase64;
                    sendMessage(dataMsg);

                    bytesSent += chunkSize;

                    // Показываем прогресс
                    int progress = (bytesSent * 100) / fileBytes.length;
                    System.out.print("\r📤 Прогресс: " + progress + "%");

                    if (fileProgressCallback != null) {
                        fileProgressCallback.onFileProgress(file.getName(), progress, true);
                    }

                    Thread.sleep(50); // Небольшая задержка между чанками
                }

                // Отправляем сообщение о завершении
                String endMsg = "FILE_END|" + file.getName();
                sendMessage(endMsg);

                System.out.println("\n✓ Файл успешно отправлен: " + file.getName());

                if (fileProgressCallback != null) {
                    fileProgressCallback.onFileProgress(file.getName(), 100, true);
                }

            } catch (Exception e) {
                System.out.println("\n✗ Ошибка отправки файла: " + e.getMessage());
            }
        }, "FileSender").start();
    }

    // =========================================================================
    // МЕТОДЫ ОБРАБОТКИ ПОЛУЧЕННЫХ ФАЙЛОВ
    // =========================================================================
    private void handleFileMessage(String message) {
        String[] parts = message.split("\\|");
        String type = parts[0];

        switch (type) {
            case "FILE_META":
                // Получаем метаданные файла
                receivingFileName = parts[1];
                receivingFileSize = Long.parseLong(parts[2]);
                receivedBytesCount = 0;

                String filePath = RECEIVED_FILES_DIR + receivingFileName;

                // Проверяем, не существует ли уже такой файл
                File file = new File(filePath);
                int counter = 1;
                while (file.exists()) {
                    String name = receivingFileName;
                    int dotIndex = name.lastIndexOf(".");
                    if (dotIndex > 0) {
                        String baseName = name.substring(0, dotIndex);
                        String extension = name.substring(dotIndex);
                        filePath = RECEIVED_FILES_DIR + baseName + "_" + counter + extension;
                    } else {
                        filePath = RECEIVED_FILES_DIR + name + "_" + counter;
                    }
                    file = new File(filePath);
                    counter++;
                }

                try {
                    fileOutputStream = new FileOutputStream(filePath);
                    System.out.println("\n📥 Прием файла: " + file.getName() +
                            " (" + receivingFileSize + " байт)");
                } catch (IOException e) {
                    System.out.println("✗ Ошибка создания файла: " + e.getMessage());
                    fileOutputStream = null;
                }
                break;

            case "FILE_DATA":
                if (fileOutputStream == null) return;

                try {
                    int chunkNumber = Integer.parseInt(parts[1]);
                    String chunkBase64 = parts[2];
                    byte[] chunk = Base64.getDecoder().decode(chunkBase64);

                    fileOutputStream.write(chunk);
                    receivedBytesCount += chunk.length;

                    int progress = (int)((receivedBytesCount * 100) / receivingFileSize);
                    System.out.print("\r📥 Прогресс: " + progress + "%");

                    if (fileProgressCallback != null) {
                        fileProgressCallback.onFileProgress(receivingFileName, progress, false);
                    }

                } catch (Exception e) {
                    System.out.println("\n✗ Ошибка записи файла: " + e.getMessage());
                }
                break;

            case "FILE_END":
                try {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                        fileOutputStream = null;
                        System.out.println("\n✓ Файл успешно получен: " + receivingFileName);

                        if (fileProgressCallback != null) {
                            fileProgressCallback.onFileProgress(receivingFileName, 100, false);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("\n✗ Ошибка закрытия файла: " + e.getMessage());
                }

                receivingFileName = null;
                receivingFileSize = 0;
                receivedBytesCount = 0;
                break;
        }
    }

    // =========================================================================
    // КЛАСС RECEIVER (ПРИЕМ СООБЩЕНИЙ)
    // =========================================================================
    class Receiver implements Runnable {
        @Override
        public void run() {
            byte[] buffer = new byte[65535]; // Увеличиваем буфер для файлов

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String received = new String(packet.getData(), 0, packet.getLength(), "UTF-8");

                    // Дешифруем сообщение
                    String message = received;
                    if (SimpleCryptoXOR.isEncrypted(received)) {
                        message = SimpleCryptoXOR.decrypt(received);
                    }

                    // Проверяем, не файловое ли это сообщение
                    if (message.startsWith("FILE_META|") ||
                            message.startsWith("FILE_DATA|") ||
                            message.startsWith("FILE_END|")) {
                        handleFileMessage(message);
                        continue; // Не выводим в чат
                    }

                    // Автоматическое определение собеседника
                    if (!partnerConnected && packet.getAddress() != null) {
                        partnerAddress = packet.getAddress();
                        partnerPort = packet.getPort();

                        String nameMsg = "NAME|" + userName;
                        sendMessage(nameMsg);

                        partnerConnected = true;

                        System.out.println("\n=== Собеседник подключился! ===");

                        if (statusCallback != null) {
                            statusCallback.onStatusChanged(true, "Собеседник");
                        }
                    }

                    // Проверяем, не является ли сообщение передачей имени
                    if (message.startsWith("NAME|")) {
                        partnerName = message.substring(5);
                        System.out.print("\r\033[K");
                        System.out.println("✓ Собеседник представился: " + partnerName);

                        if (statusCallback != null) {
                            statusCallback.onStatusChanged(true, partnerName);
                        }
                    }
                    // Выводим обычное сообщение
                    else if (partnerConnected && !message.contains("[" + userName + "]:")) {
                        System.out.print("\r\033[K");
                        String senderName = partnerName != null ? partnerName : "Собеседник";
                        System.out.println("[" + senderName + "]: " + message);

                        if (messageCallback != null) {
                            messageCallback.onMessageReceived(senderName, message);
                        }
                    }

                } catch (IOException e) {
                    if (running) {
                        System.out.println("\nОшибка приема: " + e.getMessage());
                    }
                }
            }
        }
    }

    // =========================================================================
    // ЗАПУСК ЧАТА
    // =========================================================================
    public void startReceiver() {
        new Thread(new Receiver()).start();
    }

    public void start() {
        new Thread(new Receiver()).start();
        Scanner scanner = new Scanner(System.in);

        System.out.println("Команды:");
        System.out.println(" /list - показать доступных пользователей");
        System.out.println(" /connect <номер> - подключиться к пользователю");
        System.out.println(" /sendfile <путь> - отправить файл");
        System.out.println(" /disconnect - отключиться от собеседника");
        System.out.println(" /exit - выйти из чата");
        System.out.println("------------------------");

        while (running) {
            System.out.print("[" + userName + "]: ");
            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("/exit")) {
                stop();
                break;
            }

            if (input.startsWith("/")) {
                String[] parts = input.split(" ", 2);
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "/list":
                        refreshPeersList();
                        break;

                    case "/connect":
                        if (parts.length > 1) {
                            try {
                                int index = Integer.parseInt(parts[1]);
                                connectToPeer(index);
                            } catch (NumberFormatException e) {
                                System.out.println("Использование: /connect <номер>");
                            }
                        } else {
                            System.out.println("Использование: /connect <номер>");
                        }
                        break;

                    case "/sendfile":
                        if (parts.length > 1) {
                            if (partnerConnected) {
                                sendFile(parts[1]);
                            } else {
                                System.out.println("✗ Сначала подключитесь к собеседнику");
                            }
                        } else {
                            System.out.println("Использование: /sendfile <путь_к_файлу>");
                        }
                        break;

                    case "/disconnect":
                        disconnectFromPeer();
                        break;

                    default:
                        System.out.println("Неизвестная команда");
                }
            }
            else if (!input.isEmpty()) {
                if (partnerConnected) {
                    sendMessage(input);
                } else {
                    System.out.println("Нет подключения! Используй /list и /connect");
                }
            }
        }
    }

    // =========================================================================
    // ОСТАНОВКА ЧАТА
    // =========================================================================
    public void stop() {
        running = false;
        try {
            sendToSignalServer("LOGOUT|" + userName);
        } catch (Exception e) {
            // Игнорируем
        }

        // Закрываем файл, если остался открытым
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                // Игнорируем
            }
        }

        socket.close();
        System.out.println("Чат завершен");
    }

    // =========================================================================
    // ГЛАВНЫЙ МЕТОД
    // =========================================================================
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--gui")) {
            // Запускаем GUI
            SwingUtilities.invokeLater(() -> {
                ChatGUI.main(new String[0]);
            });
        } else {
            // Консольный режим
            Scanner scanner = new Scanner(System.in);

            System.out.println("=== P2P Чат ===");
            System.out.println("Для запуска с графическим интерфейсом используйте: java SimpleP2PChat --gui");
            System.out.println();

            System.out.print("Введи свой ник: ");
            String userName = scanner.nextLine();

            System.out.print("Введи порт для прослушивания (например, 8001): ");
            int port = scanner.nextInt();
            scanner.nextLine();

            try {
                SimpleP2PChat chat = new SimpleP2PChat(port, userName);
                chat.start();
            } catch (SocketException e) {
                System.out.println("Ошибка: порт " + port + " уже используется!");
            }
        }
    }
}