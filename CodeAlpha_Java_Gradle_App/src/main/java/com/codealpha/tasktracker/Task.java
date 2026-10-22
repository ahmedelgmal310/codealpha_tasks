package com.codealpha.tasktracker;

import java.time.Instant;

/**
 * A single to-do item. Records are immutable, so completing a task returns a new instance.
 */
public record Task(long id, String title, boolean done, Instant createdAt) {

    public Task markDone() {
        return new Task(id, title, true, createdAt);
    }
}
