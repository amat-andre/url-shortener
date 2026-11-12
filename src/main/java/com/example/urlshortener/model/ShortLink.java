package com.example.urlshortener.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Сокращённая ссылка: код, оригинальный URL, владелец,
 * лимит переходов и время жизни.
 */
public class ShortLink {

    private final String code;
    private final String originalUrl;
    private final UUID ownerId;
    private int maxClicks;
    private int clickCount;
    private final Instant createdAt;
    private final Instant expiresAt;

    public ShortLink(String code, String originalUrl, UUID ownerId,
                     int maxClicks, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.ownerId = ownerId;
        this.maxClicks = maxClicks;
        this.clickCount = 0;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getCode() {
        return code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getMaxClicks() {
        return maxClicks;
    }

    public void setMaxClicks(int maxClicks) {
        this.maxClicks = maxClicks;
    }

    public int getClickCount() {
        return clickCount;
    }

    public void incrementClicks() {
        this.clickCount++;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Лимит переходов исчерпан. */
    public boolean isClickLimitExhausted() {
        return clickCount >= maxClicks;
    }

    /** Срок жизни истёк на момент {@code now}. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "ShortLink{code='" + code + "', url='" + originalUrl
                + "', clicks=" + clickCount + "/" + maxClicks
                + ", expiresAt=" + expiresAt + '}';
    }
}
