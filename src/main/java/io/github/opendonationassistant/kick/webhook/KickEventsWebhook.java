package io.github.opendonationassistant.kick.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.kick.account.KickAccount;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.github.opendonationassistant.kick.metrics.KickMetrics;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Controller
public class KickEventsWebhook {

  private final ODALogger log = new ODALogger(this);
  private final KickAccountRepository accountRepository;
  private final ObjectMapper mapper;
  private final KickMetrics metrics;

  @Inject
  public KickEventsWebhook(
    KickAccountRepository accountRepository,
    ObjectMapper mapper,
    KickMetrics metrics
  ) {
    this.accountRepository = accountRepository;
    this.mapper = mapper;
    this.metrics = metrics;
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
      Map.of(
        "type",
        type,
        "version",
        Objects.toString(version, ""),
        "body",
        Objects.toString(body, "")
      )
    );
    metrics.eventReceived(type);
    return switch (type) {
      case "channel.followed" -> handleChannelFollowed(type, body);
      case "channel.subscription.new" -> handleChannelSubscribe(type, body);
      case "channel.subscription.renewal" -> handleChannelSubscribe(type, body);
      case "channel.subscription.gifts" -> handleChannelSubscriptionGift(
        type,
        body
      );
      case "channel.reward.redemption.updated" -> handleRewardEvent(type, body);
      case "livestream.status.updated" -> handleLivestreamStatusUpdated(
        type,
        body
      );
      case "livestream.metadata.updated" -> handleLivestreamMetadataUpdated(
        type,
        body
      );
      case "kicks.gifted" -> handleKicksGifted(type, body);
      default -> {
        log.warn("Unsupported kick event type", Map.of("type", type));
        metrics.eventInvalid(type);
        yield CompletableFuture.completedFuture(null);
      }
    };
  }

  private <P> CompletableFuture<Void> handle(
    String type,
    String body,
    Class<P> payloadType,
    Function<P, String> kickId,
    BiConsumer<KickAccount, P> action
  ) {
    try {
      var payload = mapper.readValue(body, payloadType);
      if (payload == null) {
        log.error("Empty %s event".formatted(type), Map.of("type", type));
        metrics.eventInvalid(type);
        return CompletableFuture.completedFuture(null);
      }
      var id = kickId.apply(payload);
      if (id == null) {
        log.error(
          "Missing broadcaster in %s event".formatted(type),
          Map.of("type", type)
        );
        metrics.eventInvalid(type);
        return CompletableFuture.completedFuture(null);
      }
      return accountRepository
        .findByKickId(id)
        .thenAccept(it ->
          it.ifPresentOrElse(
            account -> action.accept(account, payload),
            () -> {
              log.info("Account not found", Map.of("kickId", id));
              metrics.eventNoAccount(type);
            }
          )
        );
    } catch (Exception e) {
      // SerdeException/JsonSyntaxException extend IOException, while missing
      // record components and other incomplete payloads surface as
      // RuntimeException; both mean the delivery is invalid.
      log.error(
        "Failed to parse %s event".formatted(type),
        Map.of("type", type, "error", Objects.toString(e.getMessage(), ""))
      );
      metrics.eventInvalid(type);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> handleChannelFollowed(
    String type,
    String body
  ) {
    return handle(
      type,
      body,
      KickChannelFollowedPayload.class,
      payload -> payload.broadcaster().id(),
      (account, payload) -> account.handleFollow(payload.follower().username())
    );
  }

  private CompletableFuture<Void> handleLivestreamStatusUpdated(
    String type,
    String body
  ) {
    return handle(
      type,
      body,
      KickLivestreamStatusUpdatedPayload.class,
      payload -> payload.broadcaster().id(),
      (account, payload) -> {
        if (!payload.isLive()) {
          log.info("Stream ended, ignoring", Map.of());
          return;
        }
        account.handleStreamChange(
          payload.title(),
          payload.broadcaster().profilePicture(),
          payload.startedAt()
        );
      }
    );
  }

  private CompletableFuture<Void> handleChannelSubscribe(
    String type,
    String body
  ) {
    return handle(
      type,
      body,
      KickChannelSubscribePayload.class,
      payload -> payload.broadcaster().id(),
      (account, payload) ->
        account.handleSubscription(
          payload.subscriber().username(),
          payload.duration(),
          payload.createdAt()
        )
    );
  }

  private CompletableFuture<Void> handleChannelSubscriptionGift(
    String type,
    String body
  ) {
    return handle(
      type,
      body,
      KickChannelSubscriptionGiftPayload.class,
      payload -> payload.broadcaster().id(),
      (account, payload) ->
        account.handleSubscriptionGift(
          payload.gifter().username(),
          payload.giftees().size(),
          payload.createdAt()
        )
    );
  }

  private CompletableFuture<Void> handleRewardEvent(String type, String body) {
    return handle(
      type,
      body,
      KickRewardPayload.class,
      event -> event.broadcaster().id(),
      (account, event) -> account.handleReward(event)
    );
  }

  private CompletableFuture<Void> handleLivestreamMetadataUpdated(
    String type,
    String body
  ) {
    return handle(
      type,
      body,
      KickLivestreamMetadataUpdatedPayload.class,
      payload -> payload.broadcaster().userId(),
      (account, payload) ->
        account.handleMetadataUpdate(
          payload.metadata().title(),
          payload.metadata().category().name(),
          payload.metadata().language()
        )
    );
  }

  private CompletableFuture<Void> handleKicksGifted(String type, String body) {
    return handle(
      type,
      body,
      KickKicksGiftedPayload.class,
      payload -> payload.broadcaster().userId(),
      (account, payload) ->
        account.handleKicksGifted(
          payload.sender().username(),
          payload.gift().name(),
          payload.gift().type(),
          payload.gift().tier(),
          payload.gift().amount(),
          payload.createdAt()
        )
    );
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
  public static record Reward(String title, String id, Integer cost) {}

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
