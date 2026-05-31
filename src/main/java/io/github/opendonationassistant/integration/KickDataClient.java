package io.github.opendonationassistant.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Client("kick-data")
public interface KickDataClient {
  @Post("/public/v1/events/subscriptions")
  CompletableFuture<DataWrapper<List<CreatedSubscription>>> subscribe(
    @Header("Authorization") String token,
    @Body SubscriptionRequest request
  );

  @Delete("/public/v1/events/subscriptions")
  CompletableFuture<Void> unsubscribe(
    @Header("Authorization") String token,
    @QueryValue("id") @Format("multi") List<String> ids
  );

  @Post("/public/v1/channels/rewards")
  CompletableFuture<DataWrapper<Created>> createReward(
    @Header("Authorization") String token,
    @Body RewardRequest request
  );

  @Delete("/public/v1/channels/rewards/{id}")
  CompletableFuture<Void> deleteReward(
    @Header("Authorization") String token,
    String id
  );

  @Serdeable
  public static record RewardRequest(
    String title,
    String description,
    Integer cost,
    @JsonProperty("is_user_input_required") Boolean isUserInputRequired,
    String backgroundColor
  ) {}

  @Serdeable
  public static record SubscriptionRequest(
    List<EventSubscription> events,
    String method
  ) {}

  @Serdeable
  public static record CreatedSubscription(
    @JsonProperty("subscription_id") String id,
    String name,
    Integer version
  ) {}

  @Serdeable
  public static record EventSubscription(String name, Integer version) {}

  @Serdeable
  public static record DataWrapper<T>(T data) {}

  @Serdeable
  public static record Created(String id) {}
}
