package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record KickChannelFollowEvent(
  String id,
  String recipientId,
  String username,
  Instant timestamp
) implements HasRecipientId {}
