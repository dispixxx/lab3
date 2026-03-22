package com.disp;

public class PeerInfo {
    private String name;
    private String ip;
    private int port;
    private long lastSeen;
    private boolean isActive;

    // Конструктор для нового пира
    public PeerInfo(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
    }

    // Конструктор для БД
    public PeerInfo(String name, String ip, int port, long lastSeen, boolean isActive) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.lastSeen = lastSeen;
        this.isActive = isActive;
    }

    // Геттеры и сеттеры
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    public void updateLastSeen() { this.lastSeen = System.currentTimeMillis(); }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return name + " (" + ip + ":" + port + ")";
    }
}