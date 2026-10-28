package io.github.opendonationassistant.kick.events;

import io.github.opendonationassistant.events.HasRecipientId;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record KickRewardEvent(
  String id,
  String recipientId,
  String username,
  String rewardTitle,
  String rewardId,
  String input,
  String status,
  Integer cost,
  String redeemerId,
  Instant timestamp
) implements HasRecipientId {}
