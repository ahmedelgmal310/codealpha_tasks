# Task 2 - Jenkins Remoting Project

A Jenkins controller with remote agents connected through **Jenkins Remoting**. Builds are spread across agents on two CPU architectures (**x86_64** and **aarch64**), the controller itself runs nothing, and every agent is isolated from the others. The whole setup is defined in code (Docker Compose + Jenkins Configuration as Code), so `docker compose up` gives you a working distributed Jenkins in a few minutes.

## Architecture

```
                                browser :8080
                                       │
                                       ▼
            ┌──────────────────────────────────────────────────────┐
            │ jenkins (controller)                                 │
            │ 0 executors: schedules builds, never runs them       │
            │ users, permissions, nodes and jobs from JCasC        │
            │ agent port TCP 50000 (JNLP4-connect over TLS)        │
            └──────────────────────────────────────────────────────┘
                         ▲                             ▲
                   connects out                  connects out
                         │                             │
            ┌────────────────────────┐    ┌────────────────────────┐
            │ agent-amd64            │    │ agent-arm64            │
            │ linux/amd64 (x86_64)   │    │ linux/arm64 (aarch64)  │
            │ labels: linux amd64    │    │ labels: linux arm64    │
            │ 2 executors, uid 1000  │    │ 2 executors, uid 1000  │
            └────────────────────────┘    └────────────────────────┘
               network agents-amd64          network agents-arm64
```

Each agent is on its own network: it can reach the controller but not the other agent.

### How Remoting works here

1. The controller is started with two permanent **inbound** nodes, `agent-amd64` and `agent-arm64`, defined in [`controller/casc/jenkins.yaml`](controller/casc/jenkins.yaml).
2. Each agent container runs [`agent/connect.sh`](agent/connect.sh): it logs in with the `agent-connector` account (permission **Agent/Connect** only) and downloads its node's secret.
3. It then starts `remoting.jar` (`jenkins-agent`), which opens a TCP connection to the controller on port **50000** using the **JNLP4-connect** protocol. JNLP4 runs over TLS and both sides check each other's identity.
4. Over that single connection Remoting creates a **channel**: the controller sends commands (run `sh`, copy files, stream logs) and the agent executes them in its workspace. Because the agent connects *out* to the controller, the agent machine needs no open ports, which is why inbound agents work behind NAT or firewalls.

## Task goals and where they are covered

| Goal | How |
|---|---|
| Connect remote Jenkins nodes with Remoting | Two inbound agents over JNLP4/TCP 50000; [`scripts/connect-remote-agent.sh`](scripts/connect-remote-agent.sh) connects any real machine the same way |
| Distribute build load securely | Labels + executors; `distributed-load` job spreads shards over every free executor; TLS channel; least-privilege connect account |
| Run jobs on various architectures | `agent-arm64` runs as `linux/arm64` (QEMU emulation on x86 hosts, native on ARM); `multi-arch-build` builds on both in parallel |
| Improve security with node isolation | 0 executors on the controller, `EXCLUSIVE` nodes, non-root agents, no Linux capabilities, `no-new-privileges`, separate networks per agent, CPU/memory limits; checked by `node-isolation-check` |

## Run it

Requirements: Docker Desktop (it includes QEMU for the arm64 agent) or Docker Engine with `binfmt` installed (`docker run --privileged --rm tonistiigi/binfmt --install arm64`).

```bash
cd CodeAlpha_Jenkins_Remoting
cp .env.example .env          # then set your own passwords in .env
docker compose up -d --build
docker compose ps
```

Open http://localhost:8080 and log in with `JENKINS_ADMIN_ID` / `JENKINS_ADMIN_PASSWORD` from `.env`.

- **Manage Jenkins → Nodes**: both agents should be online after 1-2 minutes. The arm64 one is slower to start because it is emulated.
- The controller (`Built-In Node`) shows **0 executors**.

The agent logs show the Remoting handshake:

```
$ docker logs agent-arm64
[agent-arm64] aarch64, waiting for the controller at http://jenkins:8080
[agent-arm64] got the secret, connecting
INFO: Remoting server accepts the following protocols: [JNLP4-connect, Ping]
INFO: Agent discovery successful
INFO: Trying protocol: JNLP4-connect
INFO: Remote identity confirmed: 38:f6:0a:2e:...
INFO: Connected
```

## Jobs

All jobs are created automatically from [`controller/casc/jobs.groovy`](controller/casc/jobs.groovy) (Job DSL). The pipeline definitions are in [`pipelines/`](pipelines).

| Job | What it shows |
|---|---|
| `multi-arch-build` | A `matrix` over `ARCH = amd64, arm64`: each branch asks for `linux && <arch>`, compiles and runs a Java program and archives `build-info-<arch>.txt` |
| `distributed-load` | Splits work into 6 shards (parameter `SHARDS`) and runs them with `parallel` on any `linux` executor, then reports which node ran each shard |
| `node-isolation-check` | Fails if a build can run on the controller, if an agent runs as root or has capabilities, or if one agent can reach the other |
| `task3-gradle-build` | Checks out this repository and runs [`CodeAlpha_Java_Gradle_App/Jenkinsfile`](../CodeAlpha_Java_Gradle_App/Jenkinsfile) on the amd64 agent (Task 3's Gradle build, remotely) |

Results from a test run on this setup:

```
multi-arch-build      SUCCESS
  agent-amd64  → Hello from amd64,  Java 21.0.12.1+1-LTS
  agent-arm64  → Hello from aarch64, Java 21.0.12.1+1-LTS

distributed-load      SUCCESS
  Shards per node:
        4 agent-amd64
        2 agent-arm64          (emulated, so it finished fewer shards)

node-isolation-check  SUCCESS
  OK: no executor available on the controller (Timeout has been exceeded)
  OK: running as jenkins (uid 1000)
  CapEff: 0000000000000000   NoNewPrivs: 1
  OK: agent-arm64 is not reachable from agent-amd64
  OK: agent-amd64 is not reachable from agent-arm64
  OK: controller is reachable

task3-gradle-build    SUCCESS
  checked out github.com/ahmedelgmal310/codealpha_tasks on agent-amd64
  Build → Test and coverage → Package, 24/24 tests passed, artifacts archived
```

## Security and node isolation

| Measure | Where | Why |
|---|---|---|
| Controller has `numExecutors: 0` | `jenkins.yaml` | Build scripts never run inside the controller process, where they could read secrets or change Jenkins itself |
| Nodes in `EXCLUSIVE` mode | `jenkins.yaml` | A node only takes jobs that explicitly ask for its labels |
| JNLP4-connect over TLS | Remoting default | Traffic between controller and agents is encrypted and both identities are verified |
| Per-node secrets | Jenkins | An agent can only connect as the node whose secret it holds |
| `agent-connector` with Agent/Connect only | `jenkins.yaml` (matrix-auth) | The agents never get the admin password |
| Anonymous access disabled, no sign-up | `jenkins.yaml` | `GET /api/json` without login returns 403 |
| Agents run as uid 1000 | agent image | A compromised build can't act as root in the container |
| `cap_drop: ALL`, `no-new-privileges` | `docker-compose.yml` | No Linux capabilities, no privilege escalation through setuid binaries |
| Separate network per agent | `docker-compose.yml` | Builds on one agent cannot reach the other agent |
| CPU/memory limits per agent | `docker-compose.yml` | One runaway build can't take the whole host down |
| Passwords only in `.env` | `.env.example`, `.gitignore` | No credentials in the repository |
| Agent → controller access control | Jenkins (always on) | Agents can't read arbitrary files on the controller |

## Adding a real remote machine

Any machine with Java 17+ can join, for example a Raspberry Pi for native ARM builds:

1. Publish the controller so the machine can reach ports `8080` and `50000` (the compose file already maps both).
2. In Jenkins: **Manage Jenkins → Nodes → New Node**, *Permanent Agent*, labels e.g. `linux arm64 raspberrypi`, launch method **Launch agent by connecting it to the controller**. Copy the secret from the node page. (Or add the node to `jenkins.yaml` to keep it in code.)
3. On the remote machine:

   ```bash
   JENKINS_SECRET=<secret> ./scripts/connect-remote-agent.sh http://<controller-ip>:8080 raspberrypi
   ```

   The script downloads `agent.jar` from the controller (so the Remoting versions always match), stores the secret in a private file instead of the command line, and connects.

If port 50000 can't be opened (strict firewall or a reverse proxy in front of Jenkins), agents can connect over **WebSocket** on the normal HTTP port instead: tick *Use WebSocket* on the node and add `-webSocket` to the `agent.jar` command.

## Troubleshooting

| Symptom | Check |
|---|---|
| Agent stays offline, log says `could not get the agent secret` | `AGENT_CONNECT_USER` / `AGENT_CONNECT_PASSWORD` must be identical for the controller and agents (both read `.env`) |
| `exec format error` in the arm64 agent | QEMU/binfmt is missing. Docker Desktop has it; on Linux run the `tonistiigi/binfmt` command above |
| arm64 agent connects slowly | Normal under emulation; it takes 1-2 minutes after the controller is up |
| Controller unhealthy, log shows `ConfigurationAsCode.init` errors | A setting in `jenkins.yaml` doesn't match the installed plugin version. The log names the exact key |
| Controller log: `connecting with the "-jnlpUrl" argument, which is deprecated` | Harmless: it's logged when `connect.sh` downloads the node's JNLP file to read the secret. The agent itself connects with `-url` and `-name` |
| Job waits with `There are no nodes with the label ...` | The label expression doesn't match any online node. Compare with **Manage Jenkins → Nodes** |

```bash
docker compose logs -f jenkins          # controller log
docker compose logs -f agent-amd64      # Remoting connection log
docker compose down                     # stop, keep jenkins_home
docker compose down -v                  # stop and delete all Jenkins data
```
