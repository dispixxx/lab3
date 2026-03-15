package com.disp;

import java.util.Base64;

public class SimpleCryptoXOR {
    private static final String KEY = "MySecretKey123"; // Простой ключ

    // Шифрование XOR
    public static String encrypt(String data) {
        if (data == null) return null;

        char[] chars = data.toCharArray();
        byte[] keyBytes = KEY.getBytes();

        for (int i = 0; i < chars.length; i++) {
            chars[i] ^= keyBytes[i % keyBytes.length];
        }

        // Кодируем в Base64 для безопасной передачи
        return Base64.getEncoder().encodeToString(new String(chars).getBytes());
    }

    // Дешифрование XOR
    public static String decrypt(String encryptedData) {
        if (encryptedData == null) return null;

        try {
            // Декодируем из Base64
            byte[] data = Base64.getDecoder().decode(encryptedData);
            String str = new String(data);
            char[] chars = str.toCharArray();
            byte[] keyBytes = KEY.getBytes();

            for (int i = 0; i < chars.length; i++) {
                chars[i] ^= keyBytes[i % keyBytes.length];
            }

            return new String(chars);
        } catch (Exception e) {
            return encryptedData; // Если не получилось расшифровать, возвращаем как есть
        }
    }

    // Проверка, зашифрованы ли данные
    public static boolean isEncrypted(String data) {
        if (data == null || data.isEmpty()) return false;

        // Проверяем, похоже ли на Base64
        try {
            Base64.getDecoder().decode(data);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}