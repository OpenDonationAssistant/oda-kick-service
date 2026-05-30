package io.github.opendonationassistant.integration;

import io.github.opendonationassistant.integration.KickDataClient.Created;
import io.github.opendonationassistant.integration.KickDataClient.CreatedSubscription;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.RewardRequest;
import io.github.opendonationassistant.integration.KickDataClient.SubscriptionRequest;
import io.github.opendonationassistant.rabbit.TokenRPC;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.zalando.problem.Problem;

@Singleton
public class KickClient {

  private final KickDataClient client;
  private final TokenRPC tokenRPC;

  @Inject
  public KickClient(KickDataClient client, TokenRPC tokenRPC) {
    this.client = client;
    this.tokenRPC = tokenRPC;
  }

  public CompletableFuture<DataWrapper<List<CreatedSubscription>>> subscribe(
    String recipientId,
    String refreshTokenId,
    SubscriptionRequest request
  ) {
    return client.subscribe(token(recipientId, refreshTokenId), request);
  }

  public CompletableFuture<Void> unsubscribe(
    String recipientId,
    String refreshTokenId,
    List<String> ids
  ) {
    return client.unsubscribe(token(recipientId, refreshTokenId), ids);
  }

  public CompletableFuture<DataWrapper<Created>> createReward(
    String recipientId,
    String refreshTokenId,
    RewardRequest request
  ) {
    return client.createReward(token(recipientId, refreshTokenId), request);
  }

  public CompletableFuture<Void> deleteReward(String token, String id) {
    return client.deleteReward(token, id);
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
