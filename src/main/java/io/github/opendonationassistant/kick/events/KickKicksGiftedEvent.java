package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import java.time.Instant;

public record KickKicksGiftedEvent(
  String id,
  String recipientId,
  String senderUsername,
  String giftName,
  String giftType,
  String giftTier,
  Integer amount,
  Instant createdAt
) implements HasRecipientId {}
