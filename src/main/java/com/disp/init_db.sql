-- Создание базы данных
CREATE DATABASE p2p_chat;

-- Подключение к базе данных
\c p2p_chat_db;

-- Создание таблицы
CREATE TABLE IF NOT EXISTS peers
(
    id                SERIAL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL UNIQUE,
    ip                VARCHAR(45)  NOT NULL,
    port              INTEGER      NOT NULL,
    first_connected   TIMESTAMP    NOT NULL,
    last_seen         TIMESTAMP    NOT NULL,
    is_active         BOOLEAN DEFAULT true,
    total_connections INTEGER DEFAULT 1
);

-- Индексы для быстрого поиска
CREATE INDEX idx_peers_name ON peers (name);
CREATE INDEX idx_peers_is_active ON peers (is_active);
CREATE INDEX idx_peers_last_seen ON peers (last_seen);

-- Пример запросов для проверки
-- SELECT * FROM peers ORDER BY last_seen DESC;
-- SELECT COUNT(*) FROM peers WHERE is_active = true;