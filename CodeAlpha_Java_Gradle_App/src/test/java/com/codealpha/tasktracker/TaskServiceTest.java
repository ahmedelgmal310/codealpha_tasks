package com.codealpha.tasktracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createAssignsIncreasingIdsAndTimestamp() {
        Task first = service.create("Write Dockerfile");
        Task second = service.create("Set up CI");

        assertThat(first.id()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(2);
        assertThat(first.done()).isFalse();
        assertThat(first.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createTrimsTheTitle() {
        assertThat(service.create("   Deploy to staging  ").title()).isEqualTo("Deploy to staging");
    }

    @Test
    void createRejectsEmptyTitles() {
        assertThatThrownBy(() -> service.create(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("   ")).hasMessage("title must not be empty");
    }

    @Test
    void createRejectsTooLongTitles() {
        String tooLong = "x".repeat(TaskService.MAX_TITLE_LENGTH + 1);

        assertThatThrownBy(() -> service.create(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 200");
        assertThat(service.create("x".repeat(TaskService.MAX_TITLE_LENGTH)).title()).hasSize(200);
    }

    @Test
    void listReturnsTasksOrderedById() {
        service.create("a");
        service.create("b");
        service.create("c");

        assertThat(service.list()).extracting(Task::title).containsExactly("a", "b", "c");
    }

    @Test
    void completeMarksTaskAsDone() {
        Task task = service.create("Review PR");

        assertThat(service.complete(task.id())).get().extracting(Task::done).isEqualTo(true);
        assertThat(service.find(task.id())).get().extracting(Task::done).isEqualTo(true);
    }

    @Test
    void completeUnknownTaskReturnsEmpty() {
        assertThat(service.complete(99)).isEmpty();
    }

    @Test
    void deleteRemovesTask() {
        Task task = service.create("Temporary");

        assertThat(service.delete(task.id())).isTrue();
        assertThat(service.find(task.id())).isEmpty();
        assertThat(service.delete(task.id())).isFalse();
    }

    @Test
    void statsCountsDoneAndOpenTasks() {
        service.create("one");
        Task two = service.create("two");
        service.create("three");
        service.complete(two.id());

        assertThat(service.stats()).isEqualTo(new TaskService.Stats(3, 1, 2));
    }
}
