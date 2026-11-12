package com.example.urlshortener.service;

import java.util.UUID;

/**
 * Консольная реализация уведомлений.
 */
public class ConsoleNotificationService implements NotificationService {

    @Override
    public void notify(UUID userId, String message) {
        System.out.println("[Уведомление для " + userId + "] " + message);
    }
}
