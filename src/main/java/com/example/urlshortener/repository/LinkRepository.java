package com.example.urlshortener.repository;

import com.example.urlshortener.model.ShortLink;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory хранилище коротких ссылок.
 */
public class LinkRepository {

    private final Map<String, ShortLink> linksByCode = new ConcurrentHashMap<>();

    public void save(ShortLink link) {
        linksByCode.put(link.getCode(), link);
    }

    public Optional<ShortLink> findByCode(String code) {
        return Optional.ofNullable(linksByCode.get(code));
    }

    public boolean exists(String code) {
        return linksByCode.containsKey(code);
    }

    public void delete(String code) {
        linksByCode.remove(code);
    }

    public List<ShortLink> findByOwner(UUID ownerId) {
        return linksByCode.values().stream()
                .filter(link -> link.getOwnerId().equals(ownerId))
                .collect(Collectors.toList());
    }

    /**
     * Удаляет все ссылки, срок жизни которых истёк на момент {@code now}.
     *
     * @return список удалённых («протухших») ссылок
     */
    public List<ShortLink> purgeExpired(Instant now) {
        List<ShortLink> expired = linksByCode.values().stream()
                .filter(link -> link.isExpired(now))
                .collect(Collectors.toList());
        expired.forEach(link -> linksByCode.remove(link.getCode()));
        return expired;
    }

    public int size() {
        return linksByCode.size();
    }
}
