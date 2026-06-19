package io.github.opendonationassistant.kick.account;

import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.kick.reward.repository.RewardDataRepository;
import io.github.opendonationassistant.rabbit.RabbitClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class KickAccountRepository {

  private final KickAccountDataRepository repository;
  private final RewardDataRepository rewardRepository;
  private final KickClient kick;
  private final RabbitClient rabbit;

  @Inject
  public KickAccountRepository(
    KickAccountDataRepository repository,
    KickClient kick,
    RewardDataRepository rewardRepository,
    RabbitClient rabbit
  ) {
    this.rewardRepository = rewardRepository;
    this.kick = kick;
    this.repository = repository;
    this.rabbit = rabbit;
  }

  public CompletableFuture<KickAccount> create(KickAccountData data) {
    repository.save(data);
    return CompletableFuture.completedFuture(
      new KickAccount(rewardRepository, kick, rabbit, data)
    );
  }

  public CompletableFuture<Optional<KickAccount>> findByKickId(String kickId) {
    return CompletableFuture.completedFuture(
      repository
        .findOneByKickId(kickId)
        .map(it -> new KickAccount(rewardRepository, kick, rabbit, it))
    );
  }

  public CompletableFuture<Optional<KickAccount>> findByRecipientId(
    String recipientId
  ) {
    return CompletableFuture.completedFuture(
      repository
        .findOneByRecipientId(recipientId)
        .map(it -> new KickAccount(rewardRepository, kick, rabbit, it))
    );
  }

  public CompletableFuture<
    Optional<KickAccount>
  > findByRecipientIdAndRefreshTokenId(
    String recipientId,
    String refreshTokenId
  ) {
    return CompletableFuture.completedFuture(
      repository
        .findOneByRecipientIdAndRefreshTokenId(recipientId, refreshTokenId)
        .map(it -> new KickAccount(rewardRepository, kick, rabbit, it))
    );
  }

  public CompletableFuture<Void> delete(
    String recipientId,
    String refreshTokenId
  ) {
    repository.deleteByRecipientIdAndRefreshTokenId(
      recipientId,
      refreshTokenId
    );
    return CompletableFuture.completedFuture(null);
  }
}
