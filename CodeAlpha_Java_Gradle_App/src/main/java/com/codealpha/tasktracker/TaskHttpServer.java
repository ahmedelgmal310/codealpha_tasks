package com.codealpha.tasktracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small JSON API on top of the JDK's built-in HTTP server.
 *
 * <pre>
 * GET    /health                    liveness check
 * GET    /api/stats                 total / done / open counts
 * GET    /api/tasks                 list tasks
 * POST   /api/tasks                 create a task: {"title": "..."}
 * GET    /api/tasks/{id}            get one task
 * POST   /api/tasks/{id}/complete   mark a task as done
 * DELETE /api/tasks/{id}            delete a task
 * </pre>
 */
public final class TaskHttpServer {

    private static final Logger log = LoggerFactory.getLogger(TaskHttpServer.class);
    private static final Pattern TASK_PATH = Pattern.compile("^/api/tasks/(\\d{1,18})(/complete)?$");

    private final TaskService service;
    private final HttpServer server;
    private final ExecutorService executor;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class,
                    (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .create();

    public TaskHttpServer(int port, TaskService service) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        // One virtual thread per request (Java 21)
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/tasks", this::handleTasks);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
        executor.shutdown();
        log.info("Server stopped");
    }

    /** The actual port, useful when the server was started on port 0. */
    public int port() {
        return server.getAddress().getPort();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            sendJson(exchange, 200, Map.of("status", "UP"));
        });
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            sendJson(exchange, 200, service.stats());
        });
    }

    private void handleTasks(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/api/tasks") || path.equals("/api/tasks/")) {
                switch (method) {
                    case "GET" -> sendJson(exchange, 200, service.list());
                    case "POST" -> createTask(exchange);
                    default -> sendError(exchange, 405, "Method not allowed");
                }
                return;
            }

            Matcher matcher = TASK_PATH.matcher(path);
            if (!matcher.matches()) {
                sendError(exchange, 404, "Not found");
                return;
            }

            long id = Long.parseLong(matcher.group(1));
            boolean completeAction = matcher.group(2) != null;

            if (completeAction) {
                if ("POST".equals(method)) {
                    sendTask(exchange, service.complete(id));
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
                return;
            }

            switch (method) {
                case "GET" -> sendTask(exchange, service.find(id));
                case "DELETE" -> {
                    if (service.delete(id)) {
                        exchange.sendResponseHeaders(204, -1);
                    } else {
                        sendError(exchange, 404, "Task " + id + " not found");
                    }
                }
                default -> sendError(exchange, 405, "Method not allowed");
            }
        });
    }

    private void createTask(HttpExchange exchange) throws IOException {
        CreateTaskRequest request;
        try (Reader body = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            request = gson.fromJson(body, CreateTaskRequest.class);
        } catch (JsonParseException e) {
            sendError(exchange, 400, "Request body must be JSON like {\"title\": \"...\"}");
            return;
        }

        if (request == null) {
            sendError(exchange, 400, "Request body must be JSON like {\"title\": \"...\"}");
            return;
        }

        try {
            Task task = service.create(request.title());
            exchange.getResponseHeaders().set("Location", "/api/tasks/" + task.id());
            sendJson(exchange, 201, task);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private void sendTask(HttpExchange exchange, Optional<Task> task) throws IOException {
        if (task.isPresent()) {
            sendJson(exchange, 200, task.get());
        } else {
            sendError(exchange, 404, "Task not found");
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message));
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    /** Runs a handler, logs the request, turns unexpected errors into a 500 and always closes the exchange. */
    private void handle(HttpExchange exchange, Handler handler) throws IOException {
        long start = System.nanoTime();
        try {
            handler.run();
        } catch (RuntimeException e) {
            log.error("Unhandled error for {} {}", exchange.getRequestMethod(), exchange.getRequestURI(), e);
            if (exchange.getResponseCode() == -1) { // nothing sent yet
                sendError(exchange, 500, "Internal server error");
            }
        } finally {
            log.info("{} {} -> {} ({} ms)", exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getResponseCode(), (System.nanoTime() - start) / 1_000_000);
            exchange.close();
        }
    }

    @FunctionalInterface
    private interface Handler {
        void run() throws IOException;
    }

    private record CreateTaskRequest(String title) {
    }
}
