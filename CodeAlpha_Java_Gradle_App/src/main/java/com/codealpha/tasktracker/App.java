package com.codealpha.tasktracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);
    static final int DEFAULT_PORT = 8080;

    private App() {
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(System.getenv("PORT"));

        TaskHttpServer server = new TaskHttpServer(port, new TaskService());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown"));

        log.info("Task Tracker {} listening on port {}", version(), server.port());
    }

    static int parsePort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        int port;
        try {
            port = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PORT must be a number, got: " + value, e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("PORT must be between 1 and 65535, got: " + port);
        }
        return port;
    }

    /** Version from the jar manifest (set in build.gradle.kts), or "dev" when running from the IDE. */
    static String version() {
        String version = App.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
