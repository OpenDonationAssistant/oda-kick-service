# PROJECT KNOWLEDGE BASE

**Generated:** 2026-11-05
**Branch:** main

## OVERVIEW

Micronaut 5.0.2 / Java 25 bridge between Kick and the ODA platform. It receives
`POST /kick/events` webhooks, publishes ODA events on the `kick` exchange, consumes Kick
commands from the `commands` exchange, and persists accounts, rewards and subscriptions in
PostgreSQL schema `kick` (Flyway). Packaged as a GraalVM native image.

## STRUCTURE

```
oda-kick-service/
├── src/main/java/io/github/opendonationassistant/
│   ├── Application.java            # main() + @Factory: AMQP init, commands/events RabbitClient beans
│   ├── CommandListener.java        # consumes `kick.commands` from the commands exchange
│   ├── WidgetChangedEventListener.java
│   ├── integration/
│   │   ├── KickDataClient.java     # @Client("kick-data") Kick public API
│   │   ├── KickClient.java         # facade over KickDataClient, token bound at construction
│   │   ├── KickClientFactory.java  # async TokenRPC lookup -> token-bound KickClient per account
│   │   ├── KickWebClient.java      # @Client("kick-web") channel info (https://kick.com)
│   │   └── media/MediaRPC.java     # RabbitMQ RPC: rpc exchange, media.add binding
│   ├── kick/
│   │   ├── account/                # KickAccount aggregate + KickAccountData(Repository) + facade
│   │   ├── webhook/                # KickEventsWebhook controller + event payload records
│   │   ├── reward/repository/      # RewardData(Repository) + KickReward domain object
│   │   ├── listener/handlers/      # typed command handlers (AbstractMessageHandler)
│   │   ├── events/                 # published ODA event records
│   │   ├── command/                # GetChannelInfo HTTP controller
│   │   ├── metrics/                # KickMetrics (`kick.events` counters)
│   │   └── subscription/           # subscription data
│   └── ...
├── src/main/resources/
│   ├── application.yml             # JWKS, executors, kick-web URL
│   ├── application-standalone.yml  # JDBC/Rabbit defaults, schema `kick`
│   ├── logback.xml
│   └── db/migration/               # Flyway V1..V3
├── src/test/java/                  # JUnit 5 + Mockito; MockServer wire test; one DB test
├── pom.xml                         # Micronaut parent 5.0.2, Java 25, ErrorProne + NullAway
├── Dockerfile                      # fedora:44, copies the pre-built native binary
└── .github/workflows/              # delegates to the oda-libraries release workflow
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Add/change a Kick event | `kick/webhook/KickEventsWebhook.java` | Controller + nested `@Serdeable` payload records |
| Change reward/media behaviour | `kick/webhook/KickEventsWebhook.java`, `kick/reward/repository/KickReward.java` | `KickAccount.reward(id)` returns the linked reward |
| Call the Kick API | `integration/KickClientFactory.java` | Async token-bound `KickClient`; the blocking `TokenRPC` runs on the `blocking` executor, raw HTTP in `KickDataClient` |
| Change the media request | `integration/media/MediaRPC.java` | `rpc` exchange, `media.add` binding |
| Persist accounts/rewards | `kick/account/`, `kick/reward/repository/` | Data records + Micronaut Data repositories |
| Add a DB migration | `src/main/resources/db/migration/` | `V{n}__description.sql` |
| Publish an ODA event | `kick/events/` + `KickAccount.sendEvent` | Sent through the `events` bean (exchange `kick`) |
| Change Rabbit wiring | `Application.java`, `CommandListener.java` | Channel initializer + queue binding declarations |
| Add a metric | `kick/metrics/KickMetrics.java` | Micrometer counters, metric name `kick.events` |
| Handle a Kick command | `kick/listener/handlers/` | Extend `AbstractMessageHandler<T>` |

## REWARD → MEDIA CONTRACT

- Kick sends `channel.reward.redemption.updated` to the anonymous `POST /kick/events`
  webhook. `KickRewardPayload` maps the JSON `id` field to `redemptionId`, plus
  `user_input`, `status`, `reward`, `redeemer`, `broadcaster`.
- The account is resolved by broadcaster Kick id. `KickAccount.reward(rewardId)` looks up
  the `RewardData` row and returns a linked `KickReward` carrying the account's
  recipient id, refresh token id, Kick channel id, `MediaRPC` and `KickClientFactory`
  (no token RPC — the client is resolved lazily inside the refund/chat path).
- Only redemptions with `status == "pending"` request media. Every status of a known
  reward still publishes a `KickRewardEvent`; an unknown reward id is only logged.
- `KickReward.addMedia(redemptionId, input, requester, title)` sends the media-add RPC
  with `id = redemptionId`, `url = input`, `requester`, `recipientId` and
  `system = "kick"`.
  **The media id is the Kick redemption id — never hash, prefix or replace it.** The
  media service's `AddMediaCommandHandler` rejects blank ids and does
  `findById(id).orElseGet(create(id, ...))`, so a redelivered redemption resolves to the
  already-created media instead of queueing it twice.
- On a null/blank media response or an RPC exception, `KickReward` refunds the redemption
  through `KickClient.rejectRedemption`
  (`POST /public/v1/channels/rewards/redemptions/reject`) and posts a Russian failure
  notice in chat. `NOT_PENDING` means the streamer already settled the redemption
  ("already processed"); a missing `data` envelope is treated as a failed refund.
  Chat/refund failures are swallowed because the webhook has already been acknowledged.
- A missing redemption id means no media request; the viewer is told that the points
  could not be returned automatically.

## CONVENTIONS

- **Null-safety is a compile gate**: ErrorProne + NullAway in JSpecify mode
  (`-Xep:NullAway:ERROR`, `.mvn/jvm.config` `--add-exports` are required).
- **Constructor injection only** (`@Inject`); DTOs are Java records annotated `@Serdeable`,
  nullable fields marked `@Nullable`.
- **Persistence split**: `XxxData` record + Micronaut Data `XxxDataRepository`; account
  rows go through the `KickAccountRepository` facade, reward rows are looked up directly
  and wrapped by `KickAccount`.
- **Linked domain objects**: `KickAccount` constructs `KickReward` with account-scoped
  dependencies (`KickClientFactory`, `MediaRPC`); `KickReward` is not a Micronaut bean.
- **Structured logging** via `ODALogger` with `Map` context. `Map.of` throws on nulls, so
  payload-derived values are wrapped with `Objects.toString(value, "")`.
- **Tests**: plain JUnit 5 + Mockito (`*Test.java`); `KickDataClientMockServerTest` uses
  MockServer for the HTTP wire contract. `LinkAccountHandlerTest` is the only
  `@MicronautTest(environments = "allinone")` test and needs Postgres.
- **Language**: code, comments and logs are English; user-facing Kick chat messages are
  Russian.
- **Git commits**: lowercase, short imperative ("fix jwks url", "remove comments").

## ANTI-PATTERNS / GOTCHAS

- **Do NOT change the media id contract** (redemption id) and do not reintroduce the old
  message-id fallback or a derived UUID: the media service deduplicates by this id. The
  `Kick-Event-Message-Id` header is no longer used.
- **Do NOT delete the `@Named("commands")` RabbitClient bean** — reward media no longer
  uses `AddMediaCommand`, but `SubscribeAllKickEventsHandler` still sends commands through
  it.
- **The webhook is `@Secured(IS_ANONYMOUS)` and does not verify `Kick-Event-Signature`.**
  It now triggers privileged side effects (media creation, redemption refund, chat), so
  treat forged deliveries as a real risk when adding handlers.
- **Reward handling runs inline on the webhook (Netty event loop) thread** and
  `TokenRPC.token` is a *blocking* RabbitMQ client: `KickClientFactory.create` dispatches
  it to the `@Named(TaskExecutors.BLOCKING)` executor and returns a `CompletableFuture`,
  so never call `tokenRPC.token` from a controller/event-loop thread (Reactor fails with
  "block()/blockFirst()/blockLast() are blocking"). `MediaRPC.add` is already
  non-blocking, and reward linking does not resolve a token at all — `KickReward` only
  creates the client on the refund/chat path.
- **Unknown reward ids must not create side effects** — no media request and no event.
- **No Maven wrapper** — use the installed `mvn`. `target/`, `.tmp/`, `.micronaut/` and
  IDE files are gitignored; don't commit them.

## COMMANDS

```bash
# Normal build/tests (includes LinkAccountHandlerTest; Test Resources provisions its
# Postgres, normally via Docker)
mvn test

# Sandboxes without Docker (and with a read-only ~/.micronaut): skip the one DB test via
# dummy datasource placeholders. This is the command used to verify this repo's suite.
mvn -B -o \
  -Dtest='!io.github.opendonationassistant.kick.listener.handlers.LinkAccountHandlerTest' \
  -Dmicronaut.test.resources.enabled=false \
  -Ddatasources.default.url='jdbc:postgresql://127.0.0.1:1/nope' \
  -Ddatasources.default.username=x -Ddatasources.default.password=x \
  -Ddatasources.default.driver-class-name=org.postgresql.Driver \
  -Ddatasources.default.initialization-fail-timeout=-1 -Ddatasources.default.minimum-idle=0 \
  -Dflyway.enabled=false -Dflyway.datasources.default.enabled=false \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn clean package -DskipTests                              # JVM jar
mvn clean package -Dpackaging=native-image -DskipTests     # native binary target/oda-kick-service
```

## NOTES

- **Environment**: `JWKS_URI` is required; `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`,
  `RABBITMQ_HOST` default to localhost in the `standalone` profile. Datasource schema is
  `kick`.
- **RabbitMQ**: `commands` queue `kick.commands` is bound to the five Kick commands;
  `events` bean publishes to the `kick` exchange; media requests use the `rpc` exchange
  with the `media.add` binding. Channel initializer binds `CommandListener` and
  `WidgetChangedEventListener`. Listener executors are single-threaded
  (`command-listener`, `event-listener`).
- **Security**: bearer JWT validated against Keycloak JWKS (`JWKS_URI`); the Kick webhook
  is anonymous (see gotchas).
- **Kick base URLs**: the `kick-web` client is pinned to `https://kick.com`
  (`micronaut.http.services.kick-web.url`). The `kick-data` client has no URL in the repo
  config; it must be supplied externally via
  `micronaut.http.services.kick-data.url` (tests override it with MockServer).
- **Shared libs**: `oda-rabbit-conf` (`AMQPConfiguration`, `RabbitClient`, `TokenRPC`,
  `MessageProcessor`, `AbstractMessageHandler`) at `oda.version` 0.11.228.
- **CI/release**: `.github/workflows/` delegates to
  `OpenDonationAssistant/oda-libraries/.github/workflows/release_service.yml@master`;
  release version = GitHub run number; npm client generation is disabled.
