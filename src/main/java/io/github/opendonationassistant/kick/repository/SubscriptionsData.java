package io.github.opendonationassistant.kick.repository;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
@MappedEntity("subscriptions")
public record SubscriptionsData(
  @Id String id,
  String recipientId,
  String tokenId,
  @MappedProperty(type = DataType.JSON) List<Subscription> events
) {
  @Serdeable
  public static record Subscription(String id, String name, Integer version) {}
}
