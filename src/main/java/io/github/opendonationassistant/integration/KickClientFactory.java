package io.github.opendonationassistant.integration;

import io.github.opendonationassistant.rabbit.AsyncTokenRPC;
import io.github.opendonationassistant.rabbit.TokenRPC;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.zalando.problem.Problem;

@Singleton
public class KickClientFactory {

  private final KickDataClient client;
  private final AsyncTokenRPC asyncTokenRPC;

  @Inject
  public KickClientFactory(
    KickDataClient client,
    AsyncTokenRPC asyncTokenRPC
  ) {
    this.client = client;
    this.asyncTokenRPC = asyncTokenRPC;
  }

  public CompletableFuture<KickClient> create(
    String recipientId,
    String refreshTokenId
  ) {
    return asyncTokenRPC
      .token(new TokenRPC.TokenRequest(recipientId, refreshTokenId))
      .thenApply(response -> new KickClient(client, bearerToken(response)));
  }

  private String bearerToken(TokenRPC.@Nullable TokenResponse response) {
    return Optional.ofNullable(response)
      .map(TokenRPC.TokenResponse::token)
      .map(token -> "Bearer " + token)
      .orElseThrow(() ->
        Problem.builder().withTitle("Can't obtain access token").build()
      );
  }
}
