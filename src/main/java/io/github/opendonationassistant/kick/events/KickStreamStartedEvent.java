package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import java.time.Instant;

public record KickStreamStartedEvent(
  String id,
  String recipientId,
  String title,
  String thumbnailUrl,
  Instant startedAt
) implements HasRecipientId {}
