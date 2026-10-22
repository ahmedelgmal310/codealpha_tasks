# Task 3 - Java Application using Gradle

**Task Tracker** is a small REST API in Java 21 built with **Gradle**. The task focuses on the build: automating compile/test/package with Gradle, managing dependencies cleanly, and running the whole thing through a **CI/CD pipeline** that ends with a Docker image published to GitHub Container Registry.

## What's in this folder

```
CodeAlpha_Java_Gradle_App/
├── build.gradle.kts            # build script: plugins, dependencies, test, coverage, jar manifest
├── settings.gradle.kts         # project name
├── gradle.properties           # build cache, parallel build, configuration cache
├── gradle/
│   ├── libs.versions.toml      # version catalog: every dependency version in one place
│   └── wrapper/                # Gradle wrapper (pins Gradle 9.8.2)
├── gradlew, gradlew.bat        # run Gradle without installing it
├── src/main/java/...           # App, TaskHttpServer, TaskService, Task
├── src/test/java/...           # 24 JUnit 5 tests (unit + HTTP integration)
├── Dockerfile                  # multi-stage: Gradle build -> small JRE image
└── Jenkinsfile                 # the same pipeline for Jenkins (runs on the Task 2 agents)
```

The GitHub Actions pipeline is in [`.github/workflows/task3-java-gradle.yml`](../.github/workflows/task3-java-gradle.yml).

## Run it

Only a JDK 21 is needed; the wrapper downloads the right Gradle version on first use.

```bash
cd CodeAlpha_Java_Gradle_App

./gradlew build        # compile, test, coverage check, jar + zip/tar distributions
./gradlew run          # start the API on http://localhost:8080
```

Windows: use `gradlew.bat build`.

No JDK installed? Build and run with Docker instead:

```bash
docker build -t task-tracker .
docker run -d -p 8080:8080 --name task-tracker task-tracker
```

Try the API:

```bash
curl localhost:8080/health
curl -X POST localhost:8080/api/tasks -d '{"title":"Set up the Gradle build"}'
curl localhost:8080/api/tasks
curl -X POST localhost:8080/api/tasks/1/complete
curl localhost:8080/api/stats
curl -X DELETE localhost:8080/api/tasks/1
```

| Method | Path | Description |
|---|---|---|
| GET | `/health` | `{"status":"UP"}` |
| GET | `/api/tasks` | List all tasks |
| POST | `/api/tasks` | Create `{"title": "..."}` → `201` with `Location` header |
| GET | `/api/tasks/{id}` | One task, `404` if missing |
| POST | `/api/tasks/{id}/complete` | Mark as done |
| DELETE | `/api/tasks/{id}` | `204`, or `404` if missing |
| GET | `/api/stats` | `{"total":..,"done":..,"open":..}` |

## How the build works

### Gradle wrapper

`gradlew` + `gradle/wrapper/gradle-wrapper.properties` pin the exact Gradle version (9.8.2). Every developer and every CI server builds with the same version, and nobody has to install Gradle. The CI pipeline also validates the wrapper jar's checksum so a tampered wrapper can't sneak into the build.

### Tasks used

| Command | What it does |
|---|---|
| `./gradlew compileJava` | Compiles with `-Xlint:all -Werror`: any compiler warning fails the build |
| `./gradlew test` | Runs JUnit 5 tests, then generates the JaCoCo coverage report |
| `./gradlew check` | Tests **and** coverage verification (fails under 80% line coverage, currently 93%) |
| `./gradlew build` | `check` + jar + zip/tar distributions |
| `./gradlew installDist` | Unpacks the distribution to `build/install/` (used by the Dockerfile) |
| `./gradlew run` | Starts the app |
| `./gradlew dependencies` | Prints the resolved dependency tree |
| `./gradlew tasks` | Lists every available task |

Reports after a build:

- Tests: `build/reports/tests/test/index.html`
- Coverage: `build/reports/jacoco/test/html/index.html`

### Dependency management

All versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) (a *version catalog*). The build script only refers to aliases like `libs.gson`, so upgrading a library is a one-line change in one file.

```kotlin
dependencies {
    implementation(libs.gson)            // needed to compile and run
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)       // only needed at runtime, not visible to the compiler

    testImplementation(platform(libs.junit.bom))   // BOM keeps all JUnit modules on the same version
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

| Configuration | Compile classpath | Runtime classpath | Ships in the app |
|---|---|---|---|
| `implementation` | yes | yes | yes |
| `runtimeOnly` | no | yes | yes |
| `testImplementation` | tests only | tests only | no |
| `testRuntimeOnly` | no | tests only | no |

The HTTP server is the JDK's own `com.sun.net.httpserver`, so the app only has two runtime dependencies (Gson and SLF4J).

### Faster builds

`gradle.properties` turns on the **build cache** (reuse outputs of tasks whose inputs didn't change), **parallel** execution and the **configuration cache** (skip the configuration phase on repeated builds). In CI, `gradle/actions/setup-gradle` persists the cache between runs.

## CI/CD pipeline (GitHub Actions)

```
push / pull request
      │
      ▼
 ┌──────────────────────────────┐
 │ build-test                   │  wrapper validation → ./gradlew build
 │                              │  upload: test + coverage reports, jar, zip/tar
 └──────────────┬───────────────┘
                │ only on push to main
                ▼
 ┌──────────────────────────────┐
 │ docker                       │  build image → run it → smoke test /health and the API
 │                              │  push ghcr.io/<owner>/task-tracker:<sha> and :latest
 └──────────────────────────────┘
```

- Pull requests run `build-test` only, so a failing test or a coverage drop blocks the merge.
- Every push to `main` produces a versioned Docker image in **GitHub Packages** (continuous delivery). Pull it with:

  ```bash
  docker pull ghcr.io/ahmedelgmal310/task-tracker:latest
  ```

The same stages are available for Jenkins in the [`Jenkinsfile`](Jenkinsfile). In Task 2 the Jenkins controller has a `task3-gradle-build` job that runs this file on a remote agent.

## Docker image

Two stages keep the final image small and free of build tools:

1. `eclipse-temurin:21-jdk`: downloads dependencies in a cached layer, then runs `installDist`.
2. `eclipse-temurin:21-jre-alpine`: copies only `build/install/task-tracker`, runs as UID 10001, health check on `/health`, JVM sized to the container memory limit (`-XX:MaxRAMPercentage=75`).
