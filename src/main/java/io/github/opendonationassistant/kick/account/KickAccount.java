package io.github.opendonationassistant.kick.account;

import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient;
import io.github.opendonationassistant.kick.reward.repository.Reward;
import io.github.opendonationassistant.kick.reward.repository.RewardData;
import io.github.opendonationassistant.kick.reward.repository.RewardDataRepository;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class KickAccount {

  private final KickAccountData data;
  private final RewardDataRepository rewardRepository;
  private final KickClient kick;

  public KickAccount(
    RewardDataRepository rewardRepository,
    KickClient kick,
    KickAccountData data
  ) {
    this.kick = kick;
    this.rewardRepository = rewardRepository;
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
}
