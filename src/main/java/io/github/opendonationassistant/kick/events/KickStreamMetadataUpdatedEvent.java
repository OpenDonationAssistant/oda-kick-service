package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record KickStreamMetadataUpdatedEvent(
  String id,
  String recipientId,
  String title,
  String category,
  String language
) implements HasRecipientId {}
