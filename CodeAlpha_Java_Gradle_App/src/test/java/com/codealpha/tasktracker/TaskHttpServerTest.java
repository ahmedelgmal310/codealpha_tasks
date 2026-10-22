package com.codealpha.tasktracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real server on a random free port and talks to it over HTTP.
 */
class TaskHttpServerTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private TaskHttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = new TaskHttpServer(0, new TaskService());
        server.start();
        baseUrl = "http://localhost:" + server.port();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void healthReturnsUp() throws Exception {
        HttpResponse<String> response = get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json; charset=utf-8");
    }

    @Test
    void createThenFetchTask() throws Exception {
        HttpResponse<String> created = send("POST", "/api/tasks", "{\"title\":\"Build with Gradle\"}");

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).hasValue("/api/tasks/1");
        assertThat(created.body()).contains("\"title\":\"Build with Gradle\"", "\"done\":false", "\"createdAt\":\"");

        HttpResponse<String> fetched = get("/api/tasks/1");
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).contains("\"id\":1");
    }

    @Test
    void listReturnsAllTasks() throws Exception {
        send("POST", "/api/tasks", "{\"title\":\"one\"}");
        send("POST", "/api/tasks", "{\"title\":\"two\"}");

        HttpResponse<String> response = get("/api/tasks");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("[").contains("\"one\"", "\"two\"");
    }

    @Test
    void completeAndStats() throws Exception {
        send("POST", "/api/tasks", "{\"title\":\"one\"}");
        send("POST", "/api/tasks", "{\"title\":\"two\"}");

        HttpResponse<String> completed = send("POST", "/api/tasks/2/complete", "");
        assertThat(completed.statusCode()).isEqualTo(200);
        assertThat(completed.body()).contains("\"done\":true");

        assertThat(get("/api/stats").body()).isEqualTo("{\"total\":2,\"done\":1,\"open\":1}");
    }

    @Test
    void deleteTask() throws Exception {
        send("POST", "/api/tasks", "{\"title\":\"remove me\"}");

        assertThat(send("DELETE", "/api/tasks/1", "").statusCode()).isEqualTo(204);
        assertThat(get("/api/tasks/1").statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/tasks/1", "").statusCode()).isEqualTo(404);
    }

    @Test
    void invalidRequestsReturn400() throws Exception {
        assertThat(send("POST", "/api/tasks", "not json").statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/tasks", "").statusCode()).isEqualTo(400);

        HttpResponse<String> blank = send("POST", "/api/tasks", "{\"title\":\"  \"}");
        assertThat(blank.statusCode()).isEqualTo(400);
        assertThat(blank.body()).isEqualTo("{\"error\":\"title must not be empty\"}");
    }

    @Test
    void unknownTaskAndPathsReturn404() throws Exception {
        assertThat(get("/api/tasks/42").statusCode()).isEqualTo(404);
        assertThat(send("POST", "/api/tasks/42/complete", "").statusCode()).isEqualTo(404);
        assertThat(get("/api/tasks/abc").statusCode()).isEqualTo(404);
    }

    @Test
    void wrongMethodsReturn405() throws Exception {
        assertThat(send("PUT", "/api/tasks", "{}").statusCode()).isEqualTo(405);
        assertThat(send("PUT", "/api/tasks/1", "{}").statusCode()).isEqualTo(405);
        assertThat(get("/api/tasks/1/complete").statusCode()).isEqualTo(405);
        assertThat(send("POST", "/health", "").statusCode()).isEqualTo(405);
        assertThat(send("DELETE", "/api/stats", "").statusCode()).isEqualTo(405);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
