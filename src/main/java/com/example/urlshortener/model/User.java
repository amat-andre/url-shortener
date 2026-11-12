package com.example.urlshortener.model;

import java.util.UUID;

/**
 * Пользователь сервиса. Авторизации нет: идентификация по UUID,
 * который генерируется при первом запросе на создание ссылки.
 */
public class User {

    private final UUID id;

    public User(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public String toString() {
        return "User{id=" + id + '}';
    }
}
