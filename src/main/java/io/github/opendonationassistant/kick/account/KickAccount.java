package io.github.opendonationassistant.kick.account;

import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient;
import io.github.opendonationassistant.kick.reward.repository.Reward;
import io.github.opendonationassistant.kick.reward.repository.RewardData;
import io.github.opendonationassistant.kick.reward.repository.RewardDataRepository;
import io.github.opendonationassistant.kick.webhook.KickEventsWebhook.KickEvent;
import io.github.opendonationassistant.rabbit.RabbitClient;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class KickAccount {

  private ODALogger log = new ODALogger(this);
  private final KickAccountData data;
  private final RewardDataRepository rewardRepository;
  private final KickClient kick;
  private final RabbitClient rabbit;

  public KickAccount(
    RewardDataRepository rewardRepository,
    KickClient kick,
    RabbitClient rabbit,
    KickAccountData data
  ) {
    this.kick = kick;
    this.rewardRepository = rewardRepository;
    this.rabbit = rabbit;
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

  public void handleEvent(KickEvent event) {
    rewardRepository
      .findById(event.reward().id())
      .ifPresentOrElse(
        reward -> {
          rabbit.sendCommand(
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
