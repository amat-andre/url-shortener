package com.example.urlshortener;

import com.example.urlshortener.config.AppConfig;
import com.example.urlshortener.model.ShortLink;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.ConsoleNotificationService;
import com.example.urlshortener.service.ShortCodeGenerator;
import com.example.urlshortener.service.UrlShortenerService;

import java.awt.Desktop;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Консольное приложение сервиса сокращения ссылок.
 *
 * Пользователь идентифицируется по UUID без авторизации:
 * UUID генерируется при первом создании ссылки, далее его нужно вводить,
 * чтобы управлять своими ссылками.
 */
public class ConsoleApp {

    private final UrlShortenerService service;
    private final Scanner scanner = new Scanner(System.in);
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private UUID currentUserId;

    public ConsoleApp(UrlShortenerService service) {
        this.service = service;
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        UrlShortenerService service = new UrlShortenerService(
                new LinkRepository(),
                new ShortCodeGenerator(),
                new ConsoleNotificationService(),
                config,
                Clock.systemUTC());
        new ConsoleApp(service).run();
    }

    public void run() {
        // фоновая очистка «протухших» ссылок раз в минуту
        cleaner.scheduleAtFixedRate(service::purgeExpired, 1, 1, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(cleaner::shutdownNow));

        System.out.println("=== Сервис сокращения ссылок ===");
        printHelp();
        while (true) {
            System.out.print("\n> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }
            String[] parts = input.split("\\s+", 2);
            String command = parts[0].toLowerCase();
            String arg = parts.length > 1 ? parts[1] : "";
            try {
                switch (command) {
                    case "login" -> login(arg);
                    case "create" -> create(arg);
                    case "open" -> open(arg);
                    case "list" -> list();
                    case "limit" -> changeLimit(arg);
                    case "delete" -> delete(arg);
                    case "help" -> printHelp();
                    case "exit" -> {
                        System.out.println("Пока!");
                        cleaner.shutdownNow();
                        return;
                    }
                    // ввод короткой ссылки без команды — переход в браузере
                    default -> open(input);
                }
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
        cleaner.shutdownNow();
    }

    private void login(String arg) {
        try {
            currentUserId = UUID.fromString(arg.trim());
        } catch (IllegalArgumentException e) {
            System.out.println("Некорректный UUID. Пример: login 11111111-2222-3333-4444-555555555555");
            return;
        }
        System.out.println("Вы вошли как пользователь " + currentUserId);
    }

    private void create(String url) {
        if (url.isBlank()) {
            System.out.println("Использование: create <длинный URL> [лимит переходов]");
            return;
        }
        String[] parts = url.split("\\s+");
        int maxClicks = 0;
        if (parts.length > 1) {
            try {
                maxClicks = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("Лимит должен быть целым числом, например: create https://example.com 10");
                return;
            }
        }
        if (currentUserId == null) {
            currentUserId = UUID.randomUUID();
            System.out.println("Создан новый пользователь. Ваш UUID (сохраните его!): " + currentUserId);
        }
        String shortUrl = service.shorten(currentUserId, parts[0], maxClicks);
        System.out.println("Короткая ссылка: " + shortUrl);
    }

    private void open(String shortUrlOrCode) throws Exception {
        if (shortUrlOrCode.isBlank()) {
            System.out.println("Использование: open <короткая ссылка или код>");
            return;
        }
        String originalUrl = service.resolve(service.extractCode(shortUrlOrCode));
        System.out.println("Открываю " + originalUrl + " ...");
        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(originalUrl));
        } else {
            System.out.println("Браузер недоступен в этой среде. URL: " + originalUrl);
        }
    }

    private void list() {
        requireLogin();
        List<ShortLink> links = service.listUserLinks(currentUserId);
        if (links.isEmpty()) {
            System.out.println("У вас пока нет активных ссылок.");
            return;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_TIME.withZone(ZoneId.systemDefault());
        System.out.println("Ваши ссылки:");
        for (ShortLink link : links) {
            System.out.printf("  %s -> %s | переходы %d/%d | действует до %s%n",
                    service.buildShortUrl(link.getCode()), link.getOriginalUrl(),
                    link.getClickCount(), link.getMaxClicks(),
                    fmt.format(link.getExpiresAt()));
        }
    }

    private void changeLimit(String arg) {
        requireLogin();
        String[] parts = arg.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Использование: limit <код ссылки> <новый лимит>");
            return;
        }
        int newLimit;
        try {
            newLimit = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            System.out.println("Лимит должен быть целым числом, например: limit " + parts[0] + " 10");
            return;
        }
        service.updateMaxClicks(currentUserId, service.extractCode(parts[0]), newLimit);
        System.out.println("Лимит обновлён.");
    }

    private void delete(String code) {
        requireLogin();
        if (code.isBlank()) {
            System.out.println("Использование: delete <код ссылки>");
            return;
        }
        service.deleteLink(currentUserId, service.extractCode(code));
        System.out.println("Ссылка удалена.");
    }

    private void requireLogin() {
        if (currentUserId == null) {
            throw new IllegalStateException(
                    "Сначала войдите: login <ваш UUID> или создайте первую ссылку командой create");
        }
    }

    private static void printHelp() {
        System.out.println("""
                Команды:
                  create <URL> [лимит]   — сократить ссылку (UUID создаётся при первом вызове)
                  open <ссылка|код>      — перейти по короткой ссылке (откроется браузер)
                  login <UUID>           — войти под своим UUID
                  list                   — мои ссылки
                  limit <код> <число>    — изменить лимит переходов (только владелец)
                  delete <код>           — удалить ссылку (только владелец)
                  exit                   — выход
                Совет: можно просто вставить короткую ссылку — выполнится переход.""");
    }
}
