package io.github.opendonationassistant.kick.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;
import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.github.opendonationassistant.kick.events.KickChannelFollowEvent;
import io.github.opendonationassistant.rabbit.RabbitClient;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Controller
public class KickEventsWebhook {

  private final ODALogger log = new ODALogger(this);
  private final KickAccountRepository accountRepository;
  private final RabbitClient kickFacade;
  private final ObjectMapper mapper;

  @Inject
  public KickEventsWebhook(
    KickAccountRepository accountRepository,
    @Named("events") RabbitClient kickFacade,
    ObjectMapper mapper
  ) {
    this.accountRepository = accountRepository;
    this.kickFacade = kickFacade;
    this.mapper = mapper;
  }

  @Post("/kick/events")
  @Operation(hidden = true)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public CompletableFuture<Void> webhook(
    @Header("Kick-Event-Type") String type,
    @Header("Kick-Event-Version") String version,
    @Body String body
  ) {
    log.info(
      "Received kick event",
      Map.of("type", type, "version", version, "body", body)
    );
    if ("channel.followed".equals(type)) {
      return handleChannelFollowed(body);
    }
    return handleRewardEvent(body);
  }

  private CompletableFuture<Void> handleChannelFollowed(String body) {
    try {
      var payload = java.util.Objects.requireNonNull(
        mapper.readValue(body, KickChannelFollowedPayload.class)
      );
      return accountRepository
        .findByKickId(payload.broadcaster().id())
        .thenAccept(it ->
          it.ifPresentOrElse(
            account -> {
              var followEvent = new KickChannelFollowEvent(
                Generators.timeBasedEpochGenerator().generate().toString(),
                account.data().recipientId(),
                payload.follower().username(),
                Instant.now()
              );
              try {
                kickFacade.sendEvent(followEvent);
              } catch (Exception e) {
                log.error(
                  "Failed to send follow event",
                  Map.of(
                    "error",
                    Optional.ofNullable(e.getMessage()).orElse("Unknown error"),
                    "recipientId",
                    account.data().recipientId()
                  )
                );
              }
            },
            () ->
              log.info(
                "Account not found",
                Map.of("kickId", payload.broadcaster().id())
              )
          )
        );
    } catch (IOException e) {
      log.error(
        "Failed to parse channel.followed event",
        Map.of("error", e.getMessage())
      );
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> handleRewardEvent(String body) {
    try {
      var event = java.util.Objects.requireNonNull(
        mapper.readValue(body, KickEvent.class)
      );
      return accountRepository
        .findByKickId(event.broadcaster().id())
        .thenAccept(it ->
          it.ifPresentOrElse(
            account -> account.handleEvent(event),
            () ->
              log.info(
                "Account not found",
                Map.of("kickId", event.broadcaster().id())
              )
          )
        );
    } catch (IOException e) {
      log.error(
        "Failed to parse reward event",
        Map.of("error", e.getMessage())
      );
      return CompletableFuture.completedFuture(null);
    }
  }

  @Serdeable
  public static record KickEvent(
    @JsonProperty("user_input") String input,
    String status,
    Reward reward,
    KickUser redeemer,
    KickUser broadcaster
  ) {}

  @Serdeable
  public static record Reward(String title, String id) {}

  @Serdeable
  public static record KickUser(
    @JsonProperty("user_id") String id,
    String username
  ) {}

  @Serdeable
  public static record KickChannelFollowedPayload(
    KickUser broadcaster,
    KickUser follower
  ) {}
}
