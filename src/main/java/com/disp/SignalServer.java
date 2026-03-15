package com.disp;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SignalServer {
    private static final int PORT = 8888;
    private static Map<String, PeerInfo> activePeers = new ConcurrentHashMap<>();
    private static DatabaseManager dbManager;

    public static void main(String[] args) {
        // Инициализируем БД
        dbManager = new DatabaseManager();

        // Загружаем активных пиров из БД при старте
//        loadActivePeersFromDB();

        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("=== Сигнальный сервер запущен на порту " + PORT + " ===");

            // Поток очистки неактивных пиров
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(30000); // Проверка каждые 30 секунд
                        long now = System.currentTimeMillis();

                        // Очищаем неактивных пиров из памяти
                        List<String> inactivePeers = new ArrayList<>();
                        for (Map.Entry<String, PeerInfo> entry : activePeers.entrySet()) {
                            if (now - entry.getValue().getLastSeen() > 60000) { // 60 секунд таймаут
                                inactivePeers.add(entry.getKey());
                            }
                        }

                        for (String name : inactivePeers) {
                            PeerInfo peer = activePeers.remove(name);
                            if (peer != null) {
                                peer.setActive(false);
                                dbManager.markPeerInactive(name);
                                System.out.println("✗ Пир отключился (таймаут): " + name);
                            }
                        }

                        // Очищаем старые записи в БД
                        dbManager.cleanupInactivePeers();

                        System.out.println("Активных пиров в памяти: " + activePeers.size());
                        System.out.println("Активных пиров в БД: " + dbManager.getActivePeers().size());

                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();

            byte[] buffer = new byte[4096];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                String[] parts = message.split("\\|");
                String response = "";
                String clientInfo = packet.getAddress().getHostAddress() + ":" + packet.getPort();

                System.out.println("Получено от " + clientInfo + ": " + message);

                if (parts[0].equals("REGISTER") && parts.length >= 3) {
                    String name = parts[1];
                    int clientPort = Integer.parseInt(parts[2]);
                    String ip = packet.getAddress().getHostAddress();

                    // Создаем объект PeerInfo
                    PeerInfo peer = new PeerInfo(name, ip, clientPort);

                    // Сохраняем в памяти
                    activePeers.put(name, peer);

                    // Сохраняем в БД
                    dbManager.saveOrUpdatePeer(name, ip, clientPort);

                    response = "REGISTERED|OK";
                    System.out.println("✓ Зарегистрирован пир: " + name + " @ " + ip + ":" + clientPort);

                    // Показываем статистику пира
                    dbManager.printPeerStats(name);
                }
                else if (parts[0].equals("HEARTBEAT") && parts.length >= 2) {
                    String name = parts[1];
                    PeerInfo peer = activePeers.get(name);
                    if (peer != null) {
                        peer.updateLastSeen();
                        response = "HEARTBEAT|OK";

                        // Обновляем last_seen в БД
                        dbManager.saveOrUpdatePeer(name, peer.getIp(), peer.getPort());
                    } else {
                        response = "HEARTBEAT|UNKNOWN";
                    }
                }
                else if (parts[0].equals("GET_PEERS")) {
                    StringBuilder sb = new StringBuilder("PEERS|");
                    boolean first = true;

                    // Получаем список из БД
                    List<PeerInfo> dbPeers = dbManager.getActivePeers();

                    for (PeerInfo peer : dbPeers) {
                        if (!first) sb.append(",");
                        sb.append(peer.getName()).append(":")
                                .append(peer.getIp()).append(":")
                                .append(peer.getPort());
                        first = false;
                    }

                    response = sb.toString();
                    System.out.println("📋 Отправлен список пиров из БД (" + dbPeers.size() + " активных)");
                }
                else if (parts[0].equals("GET_STATS") && parts.length >= 2) {
                    String name = parts[1];
                    dbManager.printPeerStats(name);
                    response = "STATS|REQUESTED";
                }
                else if (parts[0].equals("LOGOUT") && parts.length >= 2) {
                    String name = parts[1];
                    PeerInfo peer = activePeers.remove(name);
                    if (peer != null) {
                        peer.setActive(false);
                    }
                    dbManager.markPeerInactive(name);
                    response = "LOGOUT|OK";
                    System.out.println("✗ Пир отключился: " + name);
                }

                // Отправляем ответ
                if (!response.isEmpty()) {
                    byte[] respData = response.getBytes("UTF-8");
                    DatagramPacket respPacket = new DatagramPacket(
                            respData, respData.length, packet.getAddress(), packet.getPort()
                    );
                    socket.send(respPacket);
                    System.out.println("Отправлен ответ: " + response);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (dbManager != null) {
                dbManager.close();
            }
        }
    }

    private static void loadActivePeersFromDB() {
        List<PeerInfo> peers = dbManager.getActivePeers();
        for (PeerInfo peer : peers) {
            // Не загружаем в activePeers, так как они могут быть не в сети
            System.out.println("📂 Загружен из БД: " + peer.getName() +
                    " (последнее подключение: " + new Date(peer.getLastSeen()) + ")");
        }
    }
}