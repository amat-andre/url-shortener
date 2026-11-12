package com.example.urlshortener.service;

/**
 * Канал уведомлений пользователя о состоянии его ссылок.
 */
public interface NotificationService {

    /**
     * Отправляет пользователю уведомление.
     *
     * @param userId  UUID владельца ссылки
     * @param message текст уведомления
     */
    void notify(java.util.UUID userId, String message);
}
