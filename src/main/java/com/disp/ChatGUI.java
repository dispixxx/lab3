package com.disp;
import com.disp.PeerInfo;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;


public class ChatGUI extends JFrame {
    private SimpleP2PChat chat;
    private String userName;
    private int port;

    private JTextPane chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton fileButton;
    private JButton disconnectButton;
    private JList<PeerInfo> peersList;
    private DefaultListModel<PeerInfo> peersListModel;
    private JLabel statusLabel;
    private JLabel partnerLabel;
    private JProgressBar fileProgressBar;
    private JLabel progressLabel;

    private StyleContext styleContext;
    private Style systemStyle;
    private Style messageStyle;
    private Style fileStyle;
    private Style errorStyle;

    public ChatGUI(String userName, int port) {
        this.userName = userName;
        this.port = port;

        initComponents();
        setupStyles();
        startChat();
    }

    private void initComponents() {
        setTitle("P2P Чат - " + userName);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        // Главная панель
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Левая панель - список пользователей
        JPanel leftPanel = new JPanel(new BorderLayout(0, 10));
        leftPanel.setPreferredSize(new Dimension(250, 0));
        leftPanel.setBorder(new TitledBorder("Доступные пользователи"));

        peersListModel = new DefaultListModel<>();
        peersList = new JList<>(peersListModel);
        peersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        peersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    connectToPeer();
                }
            }
        });

        JScrollPane peersScroll = new JScrollPane(peersList);
        leftPanel.add(peersScroll, BorderLayout.CENTER);

        JPanel peerButtons = new JPanel(new GridLayout(2, 1, 5, 5));
        JButton refreshButton = new JButton("Обновить");
        refreshButton.addActionListener(e -> refreshPeers());

        JButton connectButton = new JButton("Подключиться");
        connectButton.addActionListener(e -> connectToPeer());

        peerButtons.add(refreshButton);
        peerButtons.add(connectButton);
        leftPanel.add(peerButtons, BorderLayout.SOUTH);

        // Центральная панель - чат
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));

        // Панель статуса
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBackground(new Color(240, 240, 240));

        statusLabel = new JLabel("● Отключен");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Dialog", Font.BOLD, 12));

        partnerLabel = new JLabel("Нет подключения");
        partnerLabel.setFont(new Font("Dialog", Font.PLAIN, 12));

        statusPanel.add(statusLabel);
        statusPanel.add(new JLabel("  |  "));
        statusPanel.add(partnerLabel);

        // Область чата
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(250, 250, 250));
        JScrollPane chatScroll = new JScrollPane(chatArea);

        // Панель ввода
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));

        messageField = new JTextField();
        messageField.setFont(new Font("Dialog", Font.PLAIN, 14));
        messageField.addActionListener(e -> sendMessage());

        sendButton = new JButton("Отправить");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        fileButton = new JButton("📎 Файл");
        fileButton.setEnabled(false);
        fileButton.addActionListener(e -> chooseFile());

        disconnectButton = new JButton("Отключиться");
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(e -> disconnect());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(fileButton);
        buttonPanel.add(sendButton);
        buttonPanel.add(disconnectButton);

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        // Панель прогресса файлов
        JPanel progressPanel = new JPanel(new BorderLayout(5, 0));
        progressPanel.setVisible(false);

        fileProgressBar = new JProgressBar(0, 100);
        fileProgressBar.setStringPainted(true);

        progressLabel = new JLabel("Передача файла...");

        progressPanel.add(progressLabel, BorderLayout.WEST);
        progressPanel.add(fileProgressBar, BorderLayout.CENTER);

        centerPanel.add(statusPanel, BorderLayout.NORTH);
        centerPanel.add(chatScroll, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(inputPanel, BorderLayout.NORTH);
        southPanel.add(progressPanel, BorderLayout.SOUTH);
        centerPanel.add(southPanel, BorderLayout.SOUTH);

        // Добавляем панели в главную
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        add(mainPanel);

        // Обработчик закрытия окна
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (chat != null) {
                    chat.stop();
                }
            }
        });
    }

    private void setupStyles() {
        styleContext = StyleContext.getDefaultStyleContext();

        // Системные сообщения (серый, курсив)
        systemStyle = styleContext.addStyle("system", null);
        StyleConstants.setForeground(systemStyle, Color.GRAY);
        StyleConstants.setItalic(systemStyle, true);

        // Обычные сообщения (черный)
        messageStyle = styleContext.addStyle("message", null);
        StyleConstants.setForeground(messageStyle, Color.BLACK);

        // Файловые сообщения (синий)
        fileStyle = styleContext.addStyle("file", null);
        StyleConstants.setForeground(fileStyle, new Color(0, 100, 200));
        StyleConstants.setBold(fileStyle, true);

        // Ошибки (красный)
        errorStyle = styleContext.addStyle("error", null);
        StyleConstants.setForeground(errorStyle, Color.RED);
        StyleConstants.setBold(errorStyle, true);
    }

    private void startChat() {
        try {
            chat = new SimpleP2PChat(port, userName);

            // Устанавливаем callbacks
            chat.setMessageCallback((sender, message) -> {
                appendMessage(sender, message, messageStyle);
            });

            chat.setStatusCallback((connected, partnerName) -> {
                SwingUtilities.invokeLater(() -> {
                    if (connected) {
                        statusLabel.setText("● Подключен");
                        statusLabel.setForeground(new Color(0, 150, 0));
                        partnerLabel.setText("Собеседник: " + partnerName);
                        sendButton.setEnabled(true);
                        fileButton.setEnabled(true);
                        disconnectButton.setEnabled(true);
                    } else {
                        statusLabel.setText("● Отключен");
                        statusLabel.setForeground(Color.RED);
                        partnerLabel.setText("Нет подключения");
                        sendButton.setEnabled(false);
                        fileButton.setEnabled(false);
                        disconnectButton.setEnabled(false);
                    }
                });
            });

            chat.setPeersCallback((peers) -> {
                SwingUtilities.invokeLater(() -> {
                    peersListModel.clear();
                    for (PeerInfo peer : peers) {
                        peersListModel.addElement(peer);
                    }
                });
            });

            chat.setFileProgressCallback((fileName, progress, isSending) -> {
                SwingUtilities.invokeLater(() -> {
                    String direction = isSending ? "Отправка" : "Получение";
                    progressLabel.setText(direction + ": " + fileName);
                    fileProgressBar.setValue(progress);

                    JPanel progressPanel = (JPanel) fileProgressBar.getParent();
                    progressPanel.setVisible(true);

                    if (progress == 100) {
                        Timer timer = new Timer(3000, e -> {
                            progressPanel.setVisible(false);
                        });
                        timer.setRepeats(false);
                        timer.start();

                        String msg = direction + " файла завершена: " + fileName;
                        appendSystemMessage(msg);
                    }
                });
            });

            chat.startReceiver();
            chat.refreshPeersList();

            appendSystemMessage("Чат запущен. Ваш ник: " + userName);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Ошибка запуска чата: " + e.getMessage(),
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshPeers() {
        if (chat != null) {
            chat.refreshPeersList();
        }
    }

    private void connectToPeer() {
        PeerInfo selected = peersList.getSelectedValue();
        if (selected != null && chat != null) {
            chat.connectToPeer(selected);
            appendSystemMessage("Подключение к " + selected.getName() + "...");
        }
    }

    private void disconnect() {
        if (chat != null) {
            chat.disconnectFromPeer();
        }
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (!text.isEmpty() && chat != null && chat.isConnected()) {
            chat.sendMessage(text);
            appendMessage("Я", text, messageStyle);
            messageField.setText("");
        }
    }

    private void chooseFile() {
        if (chat == null || !chat.isConnected()) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Выберите файл для отправки");

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            chat.sendFile(file.getAbsolutePath());
            appendSystemMessage("Начата отправка файла: " + file.getName());
        }
    }

    private void appendMessage(String sender, String message, Style style) {
        SwingUtilities.invokeLater(() -> {
            try {
                Document doc = chatArea.getDocument();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                String time = sdf.format(new Date());

                // Добавляем время
                Style timeStyle = styleContext.addStyle("time", null);
                StyleConstants.setForeground(timeStyle, Color.LIGHT_GRAY);
                StyleConstants.setFontSize(timeStyle, 11);
                doc.insertString(doc.getLength(), "[" + time + "] ", timeStyle);

                // Добавляем отправителя
                Style senderStyle = styleContext.addStyle("sender", null);
                StyleConstants.setForeground(senderStyle, new Color(0, 100, 0));
                StyleConstants.setBold(senderStyle, true);
                doc.insertString(doc.getLength(), sender + ": ", senderStyle);

                // Добавляем сообщение
                doc.insertString(doc.getLength(), message + "\n", style);

                // Прокрутка вниз
                chatArea.setCaretPosition(doc.getLength());

            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void appendSystemMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                Document doc = chatArea.getDocument();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                String time = sdf.format(new Date());

                doc.insertString(doc.getLength(), "[" + time + "] ", systemStyle);
                doc.insertString(doc.getLength(), "*** " + message + " ***\n", systemStyle);

                chatArea.setCaretPosition(doc.getLength());

            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Диалог входа
            JTextField nameField = new JTextField();
            JTextField portField = new JTextField("8001");

            Object[] message = {
                    "Ваш ник:", nameField,
                    "Порт:", portField
            };

            int option = JOptionPane.showConfirmDialog(null, message,
                    "Вход в чат", JOptionPane.OK_CANCEL_OPTION);

            if (option == JOptionPane.OK_OPTION) {
                String userName = nameField.getText().trim();
                String portText = portField.getText().trim();

                if (userName.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Введите ник!");
                    return;
                }

                try {
                    int port = Integer.parseInt(portText);
                    new ChatGUI(userName, port).setVisible(true);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null, "Неверный порт!");
                }
            }
        });
    }
}