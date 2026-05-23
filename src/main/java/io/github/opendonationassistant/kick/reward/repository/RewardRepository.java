package io.github.opendonationassistant.kick.reward.repository;

import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.rabbit.RabbitClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class RewardRepository {

  private final KickClient kick;
  private final RabbitClient rabbit;

  @Inject
  public RewardRepository(KickClient kick, RabbitClient rabbit) {
    this.kick = kick;
    this.rabbit = rabbit;
  }

  public CompletableFuture<Reward> create(RewardData data) {
    return CompletableFuture.completedFuture(new Reward(data, null, null));
  }

  private Reward convert(RewardData data) {
    return new Reward(data, rabbit, kick);
  }
}
