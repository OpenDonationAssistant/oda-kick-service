package io.github.opendonationassistant.integration;

import io.github.opendonationassistant.integration.KickDataClient.Created;
import io.github.opendonationassistant.integration.KickDataClient.CreatedSubscription;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.FailedRedemption;
import io.github.opendonationassistant.integration.KickDataClient.RejectRedemptionsRequest;
import io.github.opendonationassistant.integration.KickDataClient.RewardRequest;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageRequest;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageResponse;
import io.github.opendonationassistant.integration.KickDataClient.SubscriptionRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class KickClient {

  private final KickDataClient client;
  private final String token;

  public KickClient(KickDataClient client, String token) {
    this.client = client;
    this.token = token;
  }

  public CompletableFuture<DataWrapper<List<CreatedSubscription>>> subscribe(
    SubscriptionRequest request
  ) {
    return client.subscribe(token, request);
  }

  public CompletableFuture<Void> unsubscribe(List<String> ids) {
    return client.unsubscribe(token, ids);
  }

  public CompletableFuture<DataWrapper<Created>> createReward(
    RewardRequest request
  ) {
    return client.createReward(token, request);
  }

  public CompletableFuture<Void> deleteReward(String id) {
    return client.deleteReward(token, id);
  }

  public CompletableFuture<DataWrapper<List<FailedRedemption>>> rejectRedemption(
    String redemptionId
  ) {
    return client.rejectRedemptions(
      token,
      new RejectRedemptionsRequest(List.of(redemptionId))
    );
  }

  public CompletableFuture<DataWrapper<SendChatMessageResponse>> sendChatMessage(
    SendChatMessageRequest request
  ) {
    return client.sendChatMessage(token, request);
  }
}
