package io.github.opendonationassistant.kick.account;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class KickAccountRepository {

  private final KickAccountDataRepository repository;

  @Inject
  public KickAccountRepository(KickAccountDataRepository repository) {
    this.repository = repository;
  }

  public CompletableFuture<KickAccount> create(KickAccountData data) {
    repository.save(data);
    return CompletableFuture.completedFuture(new KickAccount(data));
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
        .map(KickAccount::new)
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
