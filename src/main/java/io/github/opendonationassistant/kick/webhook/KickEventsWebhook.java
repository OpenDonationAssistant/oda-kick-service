package io.github.opendonationassistant.kick.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.opendonationassistant.commons.logging.ODALogger;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
public class KickEventsWebhook {

  private ODALogger log = new ODALogger(this);

  @Post("/kick/events")
  @Operation(hidden = true)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public CompletableFuture<Void> webhook(
    @Header("Kick-Event-Type") String type,
    @Body KickEvent event
  ) {
    log.info("Received kick event", Map.of("type", type, "body", event));
    return CompletableFuture.completedFuture(null);
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
}
