package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import java.time.Instant;

public record KickChannelFollowEvent(
  String id,
  String recipientId,
  String username,
  Instant timestamp
) implements HasRecipientId {}
