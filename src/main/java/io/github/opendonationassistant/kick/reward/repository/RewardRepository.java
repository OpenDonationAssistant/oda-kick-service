package io.github.opendonationassistant.kick.reward.repository;

import com.fasterxml.uuid.Generators;
import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient;
import io.github.opendonationassistant.integration.KickDataClient.Created;
import io.github.opendonationassistant.rabbit.RabbitClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class RewardRepository {

  private final KickClient kick;
  private final RabbitClient rabbit;
  private final RewardDataRepository dataRepository;

  @Inject
  public RewardRepository(
    KickClient kick,
    RabbitClient rabbit,
    RewardDataRepository dataRepository
  ) {
    this.kick = kick;
    this.rabbit = rabbit;
    this.dataRepository = dataRepository;
  }

  public CompletableFuture<Reward> create(
    String title,
    String widgetId,
    Integer cost,
    String recipientId,
    String refreshTokenId
  ) {
    final Created created = kick
      .createReward(
        recipientId,
        refreshTokenId,
        new KickDataClient.RewardRequest(title, "", cost, true, null)
      )
      .join()
      .data();
    var data = new RewardData(
      Generators.timeBasedEpochGenerator().generate().toString(),
      widgetId,
      recipientId,
      refreshTokenId,
      created.id()
    );
    dataRepository.save(data);
    return CompletableFuture.completedFuture(convert(data));
  }

  private Reward convert(RewardData data) {
    return new Reward(data, rabbit, kick);
  }
}
