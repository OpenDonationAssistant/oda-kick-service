package io.github.opendonationassistant.kick.account;

import io.github.opendonationassistant.integration.KickClientFactory;
import io.github.opendonationassistant.integration.media.MediaRPC;
import io.github.opendonationassistant.kick.reward.repository.RewardDataRepository;
import io.github.opendonationassistant.rabbit.RabbitClient;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class KickAccountRepository {

  private final KickAccountDataRepository repository;
  private final RewardDataRepository rewardRepository;
  private final KickClientFactory kickFactory;
  private final MediaRPC media;
  private final RabbitClient eventsRabbit;

  @Inject
  public KickAccountRepository(
    KickAccountDataRepository repository,
    KickClientFactory kickFactory,
    RewardDataRepository rewardRepository,
    MediaRPC media,
    @Named("events") RabbitClient eventsRabbit
  ) {
    this.rewardRepository = rewardRepository;
    this.kickFactory = kickFactory;
    this.repository = repository;
    this.media = media;
    this.eventsRabbit = eventsRabbit;
  }

  public CompletableFuture<KickAccount> create(KickAccountData data) {
    repository.save(data);
    return CompletableFuture.completedFuture(convert(data));
  }

  public CompletableFuture<Optional<KickAccount>> findByKickId(String kickId) {
    return CompletableFuture.completedFuture(
      repository.findOneByKickId(kickId).map(it -> convert(it))
    );
  }

  public CompletableFuture<Optional<KickAccount>> findByRefreshTokenId(
    String refreshTokenId
  ) {
    return CompletableFuture.completedFuture(
      repository.findOneByRefreshTokenId(refreshTokenId).map(it -> convert(it))
    );
  }

  public CompletableFuture<Optional<KickAccount>> findByRecipientId(
    String recipientId
  ) {
    return CompletableFuture.completedFuture(
      repository.findOneByRecipientId(recipientId).map(it -> convert(it))
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
        .map(it -> convert(it))
    );
  }

  private KickAccount convert(KickAccountData data) {
    return new KickAccount(
      rewardRepository,
      kickFactory,
      media,
      eventsRabbit,
      data
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
