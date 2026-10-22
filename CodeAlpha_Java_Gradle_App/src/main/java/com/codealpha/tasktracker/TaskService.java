package com.codealpha.tasktracker;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory task store. Thread-safe, because the HTTP server handles requests concurrently.
 */
public class TaskService {

    static final int MAX_TITLE_LENGTH = 200;

    private final Map<Long, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Clock clock;

    public TaskService() {
        this(Clock.systemUTC());
    }

    /** The clock is injectable so tests get predictable timestamps. */
    public TaskService(Clock clock) {
        this.clock = clock;
    }

    public Task create(String title) {
        String cleanTitle = validateTitle(title);
        long id = nextId.getAndIncrement();
        Task task = new Task(id, cleanTitle, false, clock.instant());
        tasks.put(id, task);
        return task;
    }

    public List<Task> list() {
        return tasks.values().stream()
                .sorted(Comparator.comparingLong(Task::id))
                .toList();
    }

    public Optional<Task> find(long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public Optional<Task> complete(long id) {
        return Optional.ofNullable(tasks.computeIfPresent(id, (key, task) -> task.markDone()));
    }

    public boolean delete(long id) {
        return tasks.remove(id) != null;
    }

    public Stats stats() {
        long done = tasks.values().stream().filter(Task::done).count();
        return new Stats(tasks.size(), done, tasks.size() - done);
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be empty");
        }
        String trimmed = title.strip();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    public record Stats(long total, long done, long open) {
    }
}
