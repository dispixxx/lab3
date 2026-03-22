package com.disp;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/p2p_chat_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres"; // Измените на свой пароль

    private Connection connection;

    public DatabaseManager() {
        try {
            // Загружаем драйвер
            Class.forName("org.postgresql.Driver");

            // Создаем соединение
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            // Создаем таблицу, если не существует
            createTableIfNotExists();

            System.out.println("✓ Подключение к БД установлено");
        } catch (Exception e) {
            System.err.println("✗ Ошибка подключения к БД: " + e.getMessage());
        }
    }

    private void createTableIfNotExists() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS peers (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) NOT NULL UNIQUE,
                ip VARCHAR(45) NOT NULL,
                port INTEGER NOT NULL,
                first_connected TIMESTAMP NOT NULL,
                last_seen TIMESTAMP NOT NULL,
                is_active BOOLEAN DEFAULT TRUE
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ Таблица peers проверена/создана");
        }
    }

    // Сохранение или обновление информации о пире
    public void saveOrUpdatePeer(String name, String ip, int port) {
        String checkSql = "SELECT id, first_connected FROM peers WHERE name = ?";
        String updateSql = """
            UPDATE peers SET 
                ip = CAST(? AS VARCHAR), 
                port = CAST(? AS INTEGER), 
                last_seen = CAST(? AS TIMESTAMP), 
                is_active = CAST(? AS BOOLEAN)
            WHERE name = CAST(? AS VARCHAR)
        """;
        String insertSql = """
            INSERT INTO peers (name, ip, port, first_connected, last_seen, is_active)
            VALUES (CAST(? AS VARCHAR), CAST(? AS VARCHAR), CAST(? AS INTEGER), 
                    CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), CAST(? AS BOOLEAN))
        """;

        try {
            // Проверяем, существует ли пир
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setString(1, name);
                ResultSet rs = checkStmt.executeQuery();

                Timestamp now = Timestamp.valueOf(LocalDateTime.now());

                if (rs.next()) {
                    // Пир существует - обновляем

                    try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                        updateStmt.setString(1, ip);
                        updateStmt.setInt(2, port);
                        updateStmt.setTimestamp(3, now);
                        updateStmt.setBoolean(4, true);
                        updateStmt.setString(5, name);
                        updateStmt.executeUpdate();

                        System.out.println("✓ Обновлен пир в БД: " + name);
                    }
                } else {
                    // Новый пир - вставляем
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                        insertStmt.setString(1, name);
                        insertStmt.setString(2, ip);
                        insertStmt.setInt(3, port);
                        insertStmt.setTimestamp(4, now);
                        insertStmt.setTimestamp(5, now);
                        insertStmt.setBoolean(6, true);
                        insertStmt.executeUpdate();

                        System.out.println("✓ Добавлен новый пир в БД: " + name);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("✗ Ошибка сохранения пира: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Отметить пира как неактивного
    public void markPeerInactive(String name) {
        String sql = "UPDATE peers SET is_active = CAST(? AS BOOLEAN) WHERE name = CAST(? AS VARCHAR)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBoolean(1, false);
            stmt.setString(2, name);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                System.out.println("✓ Пир отмечен как неактивный: " + name);
            }
        } catch (SQLException e) {
            System.err.println("✗ Ошибка обновления статуса: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Получить всех активных пиров
    public List<PeerInfo> getActivePeers() {
        List<PeerInfo> peers = new ArrayList<>();
        String sql = """
            SELECT name, ip, port, last_seen
            FROM peers 
            WHERE is_active = CAST(? AS BOOLEAN)
            ORDER BY last_seen DESC
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBoolean(1, true);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                PeerInfo peer = new PeerInfo(
                        rs.getString("name"),
                        rs.getString("ip"),
                        rs.getInt("port"),
                        rs.getTimestamp("last_seen").getTime(),
                        true
                );
                peers.add(peer);
            }
            System.out.println("✓ Получено " + peers.size() + " активных пиров из БД");

        } catch (SQLException e) {
            System.err.println("✗ Ошибка получения пиров: " + e.getMessage());
            e.printStackTrace();
        }

        return peers;
    }

    // Получить статистику пира
    public void printPeerStats(String name) {
        String sql = """
            SELECT name, ip, port, first_connected, last_seen,
                   is_active
            FROM peers 
            WHERE name = CAST(? AS VARCHAR)
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                System.out.println("\n=== Статистика пира " + name + " ===");
                System.out.println("IP: " + rs.getString("ip"));
                System.out.println("Порт: " + rs.getInt("port"));
                System.out.println("Первое подключение: " + rs.getTimestamp("first_connected"));
                System.out.println("Последнее появление: " + rs.getTimestamp("last_seen"));
                System.out.println("Активен: " + (rs.getBoolean("is_active") ? "Да" : "Нет"));
                System.out.println("================================");
            } else {
                System.out.println("Пир " + name + " не найден в БД");
            }
        } catch (SQLException e) {
            System.err.println("✗ Ошибка получения статистики: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Очистить неактивных пиров (пиры, не обновлявшие статус более 5 минут. Принудительное отключение)
    public void cleanupInactivePeers() {
        String sql = """
            UPDATE peers 
            SET is_active = CAST(? AS BOOLEAN)
            WHERE last_seen < CAST(? AS TIMESTAMP) AND is_active = CAST(? AS BOOLEAN)
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            Timestamp fiveMinutesAgo = Timestamp.valueOf(LocalDateTime.now().minusMinutes(5));
            stmt.setBoolean(1, false);
            stmt.setTimestamp(2, fiveMinutesAgo);
            stmt.setBoolean(3, true);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                System.out.println("✓ Очищено " + updated + " неактивных пиров");
            }
        } catch (SQLException e) {
            System.err.println("✗ Ошибка очистки: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Закрыть соединение
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✓ Соединение с БД закрыто");
            }
        } catch (SQLException e) {
            System.err.println("✗ Ошибка закрытия соединения: " + e.getMessage());
        }
    }
}