package io.github.opendonationassistant.integration;

import io.github.opendonationassistant.rabbit.TokenRPC;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.zalando.problem.Problem;

@Singleton
public class KickClientFactory {

  private final KickDataClient client;
  private final TokenRPC tokenRPC;
  private final Executor tokenExecutor;

  @Inject
  public KickClientFactory(
    KickDataClient client,
    TokenRPC tokenRPC,
    @Named(TaskExecutors.BLOCKING) Executor tokenExecutor
  ) {
    this.client = client;
    this.tokenRPC = tokenRPC;
    this.tokenExecutor = tokenExecutor;
  }

  public CompletableFuture<KickClient> create(
    String recipientId,
    String refreshTokenId
  ) {
    return CompletableFuture.supplyAsync(
      () -> token(recipientId, refreshTokenId),
      tokenExecutor
    ).thenApply(token -> new KickClient(client, token));
  }

  private String token(String recipientId, String refreshTokenId) {
    return Optional.ofNullable(
      tokenRPC.token(new TokenRPC.TokenRequest(recipientId, refreshTokenId))
    )
      .map(TokenRPC.TokenResponse::token)
      .map(t -> "Bearer " + t)
      .orElseThrow(() ->
        Problem.builder().withTitle("Can't obtain access token").build()
      );
  }
}
