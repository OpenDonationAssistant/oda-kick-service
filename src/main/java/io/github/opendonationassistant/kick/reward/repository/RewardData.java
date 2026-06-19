package io.github.opendonationassistant.kick.reward.repository;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity("reward")
public record RewardData(
  @Id String id,
  @MappedProperty(type = DataType.UUID) String accountId,
  @MappedProperty(type = DataType.UUID) String widgetId,
  @MappedProperty String type
) {}
