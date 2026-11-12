package com.example.urlshortener.service;

import com.example.urlshortener.config.AppConfig;
import com.example.urlshortener.model.ShortLink;
import com.example.urlshortener.repository.LinkRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ядро сервиса сокращения ссылок: создание, переходы,
 * лимит переходов, время жизни, проверка владельца.
 */
public class UrlShortenerService {

    private final LinkRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final NotificationService notificationService;
    private final AppConfig config;
    private final Clock clock;

    public UrlShortenerService(LinkRepository repository,
                               ShortCodeGenerator codeGenerator,
                               NotificationService notificationService,
                               AppConfig config,
                               Clock clock) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.notificationService = notificationService;
        this.config = config;
        this.clock = clock;
    }

    /**
     * Создаёт короткую ссылку. Код генерируется из пары
     * «UUID пользователя + URL», поэтому у разных пользователей
     * один и тот же ресурс даёт разные короткие ссылки.
     *
     * @param ownerId     UUID пользователя (генерируется при первом запросе)
     * @param originalUrl длинный URL
     * @param maxClicks   лимит переходов; если <= 0 — берётся значение по умолчанию
     * @return полная короткая ссылка вида clck.ru/XXXXXXX
     */
    public String shorten(UUID ownerId, String originalUrl, int maxClicks) {
        validateUrl(originalUrl);
        int limit = maxClicks > 0 ? maxClicks : config.getDefaultMaxClicks();

        String code = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = codeGenerator.generate(ownerId, originalUrl, attempt);
            ShortLink existing = repository.findByCode(candidate).orElse(null);
            if (existing == null) {
                code = candidate;
                break;
            }
            // Тот же пользователь сокращает тот же URL повторно — возвращаем уже созданную ссылку
            if (existing.getOwnerId().equals(ownerId)
                    && existing.getOriginalUrl().equals(originalUrl)
                    && !existing.isExpired(Instant.now(clock))) {
                return buildShortUrl(existing.getCode());
            }
        }
        if (code == null) {
            throw new IllegalStateException("Не удалось сгенерировать уникальный код");
        }

        Instant now = Instant.now(clock);
        ShortLink link = new ShortLink(code, originalUrl, ownerId, limit,
                now, now.plus(config.getLinkTtl()));
        repository.save(link);
        return buildShortUrl(code);
    }

    /**
     * Переход по короткой ссылке: проверяет срок жизни и лимит,
     * увеличивает счётчик и возвращает исходный URL.
     *
     * @throws LinkNotFoundException   ссылка не существует или удалена
     * @throws LinkExpiredException    срок жизни истёк (ссылка удаляется, владелец уведомляется)
     * @throws ClickLimitException     лимит переходов исчерпан (владелец уведомляется)
     */
    public String resolve(String code) {
        ShortLink link = repository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(
                        "Ссылка " + buildShortUrl(code) + " не найдена"));

        if (link.isExpired(Instant.now(clock))) {
            repository.delete(code);
            notificationService.notify(link.getOwnerId(),
                    "Срок жизни ссылки " + buildShortUrl(code)
                            + " истёк, ссылка удалена (" + link.getOriginalUrl() + ")");
            throw new LinkExpiredException(
                    "Срок жизни ссылки " + buildShortUrl(code) + " истёк");
        }

        if (link.isClickLimitExhausted()) {
            notificationService.notify(link.getOwnerId(),
                    "Лимит переходов по ссылке " + buildShortUrl(code)
                            + " исчерпан (" + link.getClickCount() + "/" + link.getMaxClicks() + ")");
            throw new ClickLimitException(
                    "Лимит переходов по ссылке " + buildShortUrl(code) + " исчерпан");
        }

        link.incrementClicks();
        if (link.isClickLimitExhausted()) {
            notificationService.notify(link.getOwnerId(),
                    "Переходы по ссылке " + buildShortUrl(code) + " закончились: достигнут лимит "
                            + link.getMaxClicks() + ". Ссылка больше недоступна.");
        }
        return link.getOriginalUrl();
    }

    /** Ссылки пользователя (без просроченных). */
    public List<ShortLink> listUserLinks(UUID ownerId) {
        purgeExpired();
        return repository.findByOwner(ownerId);
    }

    /** Изменить лимит переходов. Доступно только владельцу. */
    public void updateMaxClicks(UUID ownerId, String code, int newMaxClicks) {
        if (newMaxClicks <= 0) {
            throw new IllegalArgumentException("Лимит переходов должен быть положительным");
        }
        ShortLink link = getOwnedLink(ownerId, code);
        link.setMaxClicks(newMaxClicks);
    }

    /** Удалить ссылку. Доступно только владельцу. */
    public void deleteLink(UUID ownerId, String code) {
        getOwnedLink(ownerId, code);
        repository.delete(code);
    }

    /**
     * Удаляет «протухшие» ссылки и уведомляет их владельцев.
     * Вызывается лениво при обращениях и периодически из фонового потока.
     */
    public void purgeExpired() {
        for (ShortLink link : repository.purgeExpired(Instant.now(clock))) {
            notificationService.notify(link.getOwnerId(),
                    "Срок жизни ссылки " + buildShortUrl(link.getCode())
                            + " истёк, ссылка удалена (" + link.getOriginalUrl() + ")");
        }
    }

    public Optional<ShortLink> findByCode(String code) {
        return repository.findByCode(code);
    }

    public String buildShortUrl(String code) {
        return config.getBaseUrl() + "/" + code;
    }

    /** Извлекает код из полной короткой ссылки (clck.ru/XXXXXXX) или возвращает ввод как есть. */
    public String extractCode(String input) {
        String prefix = config.getBaseUrl() + "/";
        String trimmed = input.trim();
        int idx = trimmed.indexOf(prefix);
        return idx >= 0 ? trimmed.substring(idx + prefix.length()) : trimmed;
    }

    private ShortLink getOwnedLink(UUID ownerId, String code) {
        ShortLink link = repository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(
                        "Ссылка " + buildShortUrl(code) + " не найдена"));
        if (!link.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException(
                    "Редактировать и удалять ссылку может только её создатель");
        }
        return link;
    }

    private static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL не может быть пустым");
        }
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("URL должен начинаться с http:// или https://");
        }
    }

    public static class LinkNotFoundException extends RuntimeException {
        public LinkNotFoundException(String message) {
            super(message);
        }
    }

    public static class LinkExpiredException extends RuntimeException {
        public LinkExpiredException(String message) {
            super(message);
        }
    }

    public static class ClickLimitException extends RuntimeException {
        public ClickLimitException(String message) {
            super(message);
        }
    }

    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) {
            super(message);
        }
    }
}
