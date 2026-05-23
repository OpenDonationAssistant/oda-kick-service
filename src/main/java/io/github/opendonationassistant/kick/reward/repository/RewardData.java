package io.github.opendonationassistant.kick.reward.repository;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity("reward")
public record RewardData(
  @Id String id,
  String recipientId,
  String name,
  String description,
  java.time.Instant createdAt,
  java.time.Instant updatedAt
) {}
