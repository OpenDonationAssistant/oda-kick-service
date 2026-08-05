package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record KickStreamStartedEvent(
  String id,
  String recipientId,
  String title,
  String thumbnailUrl,
  Instant startedAt
) implements HasRecipientId {}
