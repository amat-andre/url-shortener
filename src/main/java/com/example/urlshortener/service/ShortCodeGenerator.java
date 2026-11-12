package com.example.urlshortener.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Генератор коротких кодов. Берёт SHA-256 от пары
 * «UUID пользователя + длинный URL», поэтому один и тот же ресурс,
 * сокращённый разными пользователями, даёт разные короткие ссылки.
 * При коллизии в хеш добавляется счётчик-попытка.
 */
public class ShortCodeGenerator {

    private static final String BASE62 =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 7;

    public String generate(UUID ownerId, String originalUrl, int attempt) {
        String input = ownerId + ":" + originalUrl + ":" + attempt;
        byte[] hash = sha256(input);
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(BASE62.charAt((hash[i] & 0xFF) % BASE62.length()));
        }
        return code.toString();
    }

    public String generate(UUID ownerId, String originalUrl) {
        return generate(ownerId, originalUrl, 0);
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
