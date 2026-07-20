package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import java.time.Instant;

public record KickChannelSubscriptionGiftEvent(
  String id,
  String recipientId,
  String username,
  Integer amount,
  Instant createdAt
) implements HasRecipientId {}
