package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import java.time.Instant;

public record KickChannelSubscribeEvent(
  String id,
  String recipientId,
  String username,
  Integer duration,
  Instant createdAt
) implements HasRecipientId {}
