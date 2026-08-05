package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record KickChannelSubscribeEvent(
  String id,
  String recipientId,
  String username,
  Integer duration,
  Instant createdAt
) implements HasRecipientId {}
