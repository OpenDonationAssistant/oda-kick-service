package io.github.opendonationassistant.kick.account;

import com.fasterxml.uuid.Generators;
import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.events.HasRecipientId;
import io.github.opendonationassistant.integration.KickClientFactory;
import io.github.opendonationassistant.integration.KickDataClient;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageRequest;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageResponse;
import io.github.opendonationassistant.integration.media.MediaRPC;
import io.github.opendonationassistant.kick.events.KickChannelFollowEvent;
import io.github.opendonationassistant.kick.events.KickChannelSubscribeEvent;
import io.github.opendonationassistant.kick.events.KickChannelSubscriptionGiftEvent;
import io.github.opendonationassistant.kick.events.KickKicksGiftedEvent;
import io.github.opendonationassistant.kick.events.KickStreamMetadataUpdatedEvent;
import io.github.opendonationassistant.kick.events.KickStreamStartedEvent;
import io.github.opendonationassistant.kick.reward.repository.KickReward;
import io.github.opendonationassistant.kick.reward.repository.RewardData;
import io.github.opendonationassistant.kick.reward.repository.RewardDataRepository;
import io.github.opendonationassistant.rabbit.RabbitClient;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class KickAccount {

  private static final String CHAT_MESSAGE_TYPE = "user";

  private final ODALogger log = new ODALogger(this);
  private final KickAccountData data;
  private final RewardDataRepository rewardRepository;
  private final KickClientFactory kickFactory;
  private final MediaRPC media;
  private final RabbitClient eventsRabbit;

  public KickAccount(
    RewardDataRepository rewardRepository,
    KickClientFactory kickFactory,
    MediaRPC media,
    RabbitClient eventsRabbit,
    KickAccountData data
  ) {
    this.kickFactory = kickFactory;
    this.rewardRepository = rewardRepository;
    this.media = media;
    this.eventsRabbit = eventsRabbit;
    this.data = data;
  }

  public KickAccountData data() {
    return data;
  }

  public Stream<KickReward> getRewardsForWidget(String widgetId) {
    return rewardRepository
      .findByWidgetId(widgetId)
      .stream()
      .map(this::convert);
  }

  public Optional<KickReward> reward(String rewardId) {
    return rewardRepository.findById(rewardId).map(this::convert);
  }

  private KickReward convert(RewardData reward) {
    return new KickReward(
      reward,
      data.recipientId(),
      data.refreshTokenId(),
      data.kickId(),
      media,
      kickFactory
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

  public void handleSubscription(
    String username,
    Integer duration,
    Instant createdAt
  ) {
    var event = new KickChannelSubscribeEvent(
      Generators.timeBasedEpochGenerator().generate().toString(),
      data.recipientId(),
      username,
      duration,
      createdAt
    );
    sendEvent(event, "subscription");
  }

  public void handleStreamChange(
    String title,
    String thumbnailUrl,
    Instant startedAt
  ) {
    var event = new KickStreamStartedEvent(
      Generators.timeBasedEpochGenerator().generate().toString(),
      data.recipientId(),
      title,
      thumbnailUrl,
      startedAt
    );
    sendEvent(event, "stream");
  }

  public void handleSubscriptionGift(
    String username,
    Integer amount,
    Instant createdAt
  ) {
    var event = new KickChannelSubscriptionGiftEvent(
      Generators.timeBasedEpochGenerator().generate().toString(),
      data.recipientId(),
      username,
      amount,
      createdAt
    );
    sendEvent(event, "subscription gift");
  }

  public void handleMetadataUpdate(
    String title,
    String category,
    String language
  ) {
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
    return kickFactory
      .create(data.recipientId(), data.refreshTokenId())
      .thenCompose(kick ->
        kick.createReward(
          new KickDataClient.RewardRequest(title, "", cost, true, null)
        )
      )
      .thenAccept(it -> {
        var created = it.data();
        if (created == null) {
          log.error(
            "Kick createReward response had no data",
            Map.of("recipientId", data.recipientId())
          );
          return;
        }
        rewardRepository.save(
          new RewardData(created.id(), data.id(), widgetId, type)
        );
      });
  }

  public CompletableFuture<DataWrapper<SendChatMessageResponse>> sendMessage(
    String content
  ) {
    return kickFactory
      .create(data.recipientId(), data.refreshTokenId())
      .thenCompose(kick ->
        kick.sendChatMessage(
          new SendChatMessageRequest(content, CHAT_MESSAGE_TYPE, data.kickId())
        )
      );
  }
}
