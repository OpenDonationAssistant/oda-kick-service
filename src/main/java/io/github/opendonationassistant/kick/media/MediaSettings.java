package io.github.opendonationassistant.kick.media;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity("media_settings")
public record MediaSettings(
  @Id String id,
  String rewardId,
  String recipientId
) {}
