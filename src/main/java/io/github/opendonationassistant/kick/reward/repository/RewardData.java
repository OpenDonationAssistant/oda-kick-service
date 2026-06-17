package io.github.opendonationassistant.kick.reward.repository;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity("reward")
public record RewardData(
  @Id @MappedProperty(type = DataType.UUID) String id,
  String widgetId,
  @MappedProperty String recipientId,
  @MappedProperty(type = DataType.UUID) String refreshTokenId,
  @MappedProperty String type
) {}
