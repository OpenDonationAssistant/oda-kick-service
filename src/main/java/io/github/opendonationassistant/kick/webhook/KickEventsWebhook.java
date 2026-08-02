package io.github.opendonationassistant.kick.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
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
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
public class KickEventsWebhook {

  private final ODALogger log = new ODALogger(this);
  private final KickAccountRepository accountRepository;
  private final ObjectMapper mapper;

  @Inject
  public KickEventsWebhook(
    KickAccountRepository accountRepository,
    ObjectMapper mapper
  ) {
    this.accountRepository = accountRepository;
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
    return switch (type) {
      case "channel.followed" -> handleChannelFollowed(body);
      case "livestream.status.updated" -> handleLivestreamStatusUpdated(body);
      case "livestream.metadata.updated" -> handleLivestreamMetadataUpdated(
        body
      );
      case "channel.subscription.new" -> handleChannelSubscribe(body);
      case "channel.subscription.renewal" -> handleChannelSubscribe(body);
      case "channel.subscription.gifts" -> handleChannelSubscriptionGift(body);
      case "kicks.gifted" -> handleKicksGifted(body);
      default -> handleRewardEvent(body);
    };
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
            account -> account.handleFollow(payload.follower().username()),
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

  private CompletableFuture<Void> handleLivestreamStatusUpdated(String body) {
    try {
      var payload = java.util.Objects.requireNonNull(
        mapper.readValue(body, KickLivestreamStatusUpdatedPayload.class)
      );
      if (!payload.isLive()) {
        log.info("Stream ended, ignoring", Map.of());
        return CompletableFuture.completedFuture(null);
      }
      return accountRepository
        .findByKickId(payload.broadcaster().id())
        .thenAccept(it ->
          it.ifPresentOrElse(
            account ->
              account.handleStreamChange(
                payload.title(),
                payload.broadcaster().profilePicture(),
                payload.startedAt()
              ),
            () ->
              log.info(
                "Account not found",
                Map.of("kickId", payload.broadcaster().id())
              )
          )
        );
    } catch (IOException e) {
      log.error(
        "Failed to parse livestream.status.updated event",
        Map.of("error", e.getMessage())
      );
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> handleChannelSubscribe(String body) {
    try {
      var payload = java.util.Objects.requireNonNull(
        mapper.readValue(body, KickChannelSubscribePayload.class)
      );
      return accountRepository
        .findByKickId(payload.broadcaster().id())
        .thenAccept(it ->
          it.ifPresentOrElse(
            account ->
              account.handleSubscription(
                payload.subscriber().username(),
                payload.duration(),
                payload.createdAt()
              ),
            () ->
              log.info(
                "Account not found",
                Map.of("kickId", payload.broadcaster().id())
              )
          )
        );
    } catch (IOException e) {
      log.error(
        "Failed to parse channel.subscription.new event",
        Map.of("error", e.getMessage())
      );
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> handleChannelSubscriptionGift(String body) {
    try {
      var payload = java.util.Objects.requireNonNull(
        mapper.readValue(body, KickChannelSubscriptionGiftPayload.class)
      );
      return accountRepository
        .findByKickId(payload.broadcaster().id())
        .thenAccept(it ->
          it.ifPresentOrElse(
            account ->
              account.handleSubscriptionGift(
                payload.gifter().username(),
                payload.giftees().size(),
                payload.createdAt()
              ),
            () ->
              log.info(
                "Account not found",
                Map.of("kickId", payload.broadcaster().id())
              )
          )
        );
    } catch (IOException e) {
      log.error(
        "Failed to parse channel.subscription.gifts event",
        Map.of("error", e.getMessage())
      );
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> handleRewardEvent(String body) {
    try {
      var event = java.util.Objects.requireNonNull(
        mapper.readValue(body, KickRewardPayload.class)
      );
      return accountRepository
        .findByKickId(event.broadcaster().id())
        .thenAccept(it ->
          it.ifPresentOrElse(
            account -> account.handleReward(event),
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

  private CompletableFuture<Void> handleLivestreamMetadataUpdated(String body) {
    try {
      var payload = java.util.Objects.requireNonNull(
        mapper.readValue(body, KickLivestreamMetadataUpdatedPayload.class)
      );
      return accountRepository
        .findByKickId(payload.broadcaster().userId())
        .thenAccept(it ->
          it.ifPresentOrElse(
            account ->
              account.handleMetadataUpdate(
                payload.metadata().title(),
                payload.metadata().category().name(),
                payload.metadata().language()
              ),
            () ->
              log.info(
                "Account not found",
                Map.of("kickId", payload.broadcaster().userId())
              )
          )
        );
    } catch (IOException e) {
      log.error(
        "Failed to parse livestream.metadata.updated event",
        Map.of("error", e.getMessage())
      );
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> handleKicksGifted(String body) {
    try {
      var payload = java.util.Objects.requireNonNull(
        mapper.readValue(body, KickKicksGiftedPayload.class)
      );
      return accountRepository
        .findByKickId(payload.broadcaster().userId())
        .thenAccept(it ->
          it.ifPresentOrElse(
            account ->
              account.handleKicksGifted(
                payload.sender().username(),
                payload.gift().name(),
                payload.gift().type(),
                payload.gift().tier(),
                payload.gift().amount(),
                payload.createdAt()
              ),
            () ->
              log.info(
                "Account not found",
                Map.of("kickId", payload.broadcaster().userId())
              )
          )
        );
    } catch (IOException e) {
      log.error(
        "Failed to parse kicks.gifted event",
        Map.of("error", e.getMessage())
      );
      return CompletableFuture.completedFuture(null);
    }
  }

  @Serdeable
  public static record KickRewardPayload(
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

  @Serdeable
  public static record KickLivestreamStatusUpdatedPayload(
    KickStreamBroadcaster broadcaster,
    @JsonProperty("is_live") boolean isLive,
    String title,
    @JsonProperty("started_at") Instant startedAt,
    @JsonProperty("ended_at") Instant endedAt
  ) {
    @Serdeable
    public static record KickStreamBroadcaster(
      @JsonProperty("user_id") String id,
      @JsonProperty("profile_picture") String profilePicture
    ) {}
  }

  @Serdeable
  public static record KickChannelSubscribePayload(
    KickUser broadcaster,
    KickUser subscriber,
    Integer duration,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("expires_at") Instant expiresAt
  ) {}

  @Serdeable
  public static record KickChannelSubscriptionGiftPayload(
    KickUser broadcaster,
    KickUser gifter,
    List<KickUser> giftees,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("expires_at") Instant expiresAt
  ) {}

  @Serdeable
  public static record KickLivestreamMetadataUpdatedPayload(
    KickMetadataBroadcaster broadcaster,
    KickMetadata metadata
  ) {
    @Serdeable
    public static record KickMetadataBroadcaster(
      @JsonProperty("user_id") String userId,
      String username,
      @JsonProperty("is_verified") boolean isVerified,
      @JsonProperty("profile_picture") String profilePicture,
      @JsonProperty("channel_slug") String channelSlug,
      @JsonProperty("is_anonymous") boolean isAnonymous
    ) {}

    @Serdeable
    public static record KickMetadata(
      String title,
      String language,
      @JsonProperty("has_mature_content") boolean hasMatureContent,
      KickCategory category
    ) {
      @Serdeable
      public static record KickCategory(
        int id,
        String name,
        String thumbnail
      ) {}
    }
  }

  @Serdeable
  public static record KickKicksGiftedPayload(
    KickGiftedBroadcaster broadcaster,
    KickGiftedUser sender,
    KickGift gift,
    @JsonProperty("created_at") Instant createdAt
  ) {
    @Serdeable
    public static record KickGiftedBroadcaster(
      @JsonProperty("user_id") String userId,
      String username,
      @JsonProperty("is_verified") boolean isVerified,
      @JsonProperty("profile_picture") String profilePicture,
      @JsonProperty("channel_slug") String channelSlug
    ) {}

    @Serdeable
    public static record KickGiftedUser(
      @JsonProperty("user_id") String userId,
      String username,
      @JsonProperty("is_verified") boolean isVerified,
      @JsonProperty("profile_picture") String profilePicture,
      @JsonProperty("channel_slug") String channelSlug
    ) {}

    @Serdeable
    public static record KickGift(
      int amount,
      String name,
      String type,
      String tier,
      String message,
      @JsonProperty("pinned_time_seconds") Integer pinnedTimeSeconds
    ) {}
  }
}
