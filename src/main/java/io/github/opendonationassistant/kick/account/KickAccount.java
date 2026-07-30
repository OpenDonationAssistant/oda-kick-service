package io.github.opendonationassistant.kick.account;

import com.fasterxml.uuid.Generators;
import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient;
import io.github.opendonationassistant.kick.events.KickChannelFollowEvent;
import io.github.opendonationassistant.kick.events.KickChannelSubscribeEvent;
import io.github.opendonationassistant.kick.events.KickChannelSubscriptionGiftEvent;
import io.github.opendonationassistant.kick.events.KickKicksGiftedEvent;
import io.github.opendonationassistant.kick.events.KickStreamMetadataUpdatedEvent;
import io.github.opendonationassistant.kick.events.KickStreamStartedEvent;
import io.github.opendonationassistant.kick.reward.repository.Reward;
import io.github.opendonationassistant.kick.reward.repository.RewardData;
import io.github.opendonationassistant.kick.reward.repository.RewardDataRepository;
import io.github.opendonationassistant.events.HasRecipientId;
import io.github.opendonationassistant.kick.webhook.KickEventsWebhook.KickRewardPayload;
import io.github.opendonationassistant.rabbit.RabbitClient;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class KickAccount {

  private final ODALogger log = new ODALogger(this);
  private final KickAccountData data;
  private final RewardDataRepository rewardRepository;
  private final KickClient kick;
  private final RabbitClient commandsRabbit;
  private final RabbitClient eventsRabbit;

  public KickAccount(
    RewardDataRepository rewardRepository,
    KickClient kick,
    RabbitClient commandsRabbit,
    RabbitClient eventsRabbit,
    KickAccountData data
  ) {
    this.kick = kick;
    this.rewardRepository = rewardRepository;
    this.commandsRabbit = commandsRabbit;
    this.eventsRabbit = eventsRabbit;
    this.data = data;
  }

  public KickAccountData data() {
    return data;
  }

  public Stream<Reward> getRewardsForWidget(String widgetId) {
    return rewardRepository
      .findByWidgetId(widgetId)
      .stream()
      .map(it -> new Reward(it));
  }

  public void handleReward(KickRewardPayload event) {
    rewardRepository
      .findById(event.reward().id())
      .ifPresentOrElse(
        reward -> {
          commandsRabbit.sendCommand(
            new AddMediaCommand(
              event.input(),
              event.redeemer().username(),
              data().recipientId(),
              "kick"
            )
          );
        },
        () -> {
          log.info("reward not found", Map.of("rewardId", event.reward().id()));
        }
      );
  }

  public void handleFollow(String username) {
    var event = new KickChannelFollowEvent(
      Generators.timeBasedEpochGenerator().generate().toString(),
      data.recipientId(),
      username,
      Instant.now()
    );
    sendEvent(event, "follow");
  }

  public void handleSubscription(String username, Integer duration, Instant createdAt) {
    var event = new KickChannelSubscribeEvent(
      Generators.timeBasedEpochGenerator().generate().toString(),
      data.recipientId(),
      username,
      duration,
      createdAt
    );
    sendEvent(event, "subscription");
  }

  public void handleStreamChange(String title, String thumbnailUrl, Instant startedAt) {
    var event = new KickStreamStartedEvent(
      Generators.timeBasedEpochGenerator().generate().toString(),
      data.recipientId(),
      title,
      thumbnailUrl,
      startedAt
    );
    sendEvent(event, "stream");
  }

  public void handleSubscriptionGift(String username, Integer amount, Instant createdAt) {
    var event = new KickChannelSubscriptionGiftEvent(
      Generators.timeBasedEpochGenerator().generate().toString(),
      data.recipientId(),
      username,
      amount,
      createdAt
    );
    sendEvent(event, "subscription gift");
  }

  public void handleMetadataUpdate(String title, String category, String language) {
    var event = new KickStreamMetadataUpdatedEvent(
      Generators.timeBasedEpochGenerator().generate().toString(),
      data.recipientId(),
      title,
      category,
      language
    );
    sendEvent(event, "metadata update");
  }

  public void handleKicksGifted(
    String senderUsername,
    String giftName,
    String giftType,
    String giftTier,
    Integer amount,
    Instant createdAt
  ) {
    var event = new KickKicksGiftedEvent(
      Generators.timeBasedEpochGenerator().generate().toString(),
      data.recipientId(),
      senderUsername,
      giftName,
      giftType,
      giftTier,
      amount,
      createdAt
    );
    sendEvent(event, "kicks gifted");
  }

  private void sendEvent(HasRecipientId event, String type) {
    try {
      eventsRabbit.sendEvent(event);
    } catch (Exception e) {
      log.error(
        "Failed to send %s event".formatted(type),
        Map.of(
          "error",
          Optional.ofNullable(e.getMessage()).orElse("Unknown error"),
          "recipientId",
          data.recipientId()
        )
      );
    }
  }

  public CompletableFuture<Void> createReward(
    String widgetId,
    String type,
    String title,
    Integer cost
  ) {
    return kick
      .createReward(
        data.recipientId(),
        data.refreshTokenId(),
        new KickDataClient.RewardRequest(title, "", cost, true, null)
      )
      .thenAccept(it ->
        rewardRepository.save(
          new RewardData(it.data().id(), data.id(), widgetId, type)
        )
      );
  }

  @Serdeable
  public static record AddMediaCommand(
    String url,
    String requester,
    String recipientId,
    String system
  ) {}
}
