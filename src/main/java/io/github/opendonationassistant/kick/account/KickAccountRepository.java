package io.github.opendonationassistant.kick.account;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class KickAccountRepository {

  private final KickAccountDataRepository repository;

  @Inject
  public KickAccountRepository(KickAccountDataRepository repository) {
    this.repository = repository;
  }

  public CompletableFuture<KickAccount> create(KickAccountData data) {
    return CompletableFuture.completedFuture(new KickAccount(data));
  }
}
