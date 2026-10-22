package com.codealpha.tasktracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppTest {

    @Test
    void defaultPortWhenUnset() {
        assertThat(App.parsePort(null)).isEqualTo(App.DEFAULT_PORT);
        assertThat(App.parsePort("  ")).isEqualTo(App.DEFAULT_PORT);
    }

    @Test
    void parsesValidPort() {
        assertThat(App.parsePort("9090")).isEqualTo(9090);
        assertThat(App.parsePort(" 3000 ")).isEqualTo(3000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "65536", "-1"})
    void rejectsInvalidPorts(String value) {
        assertThatThrownBy(() -> App.parsePort(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void versionFallsBackToDevOutsideAJar() {
        assertThat(App.version()).isNotBlank();
    }
}
