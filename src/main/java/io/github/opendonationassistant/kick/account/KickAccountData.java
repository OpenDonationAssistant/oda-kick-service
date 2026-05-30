package io.github.opendonationassistant.kick.account;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity("accounts")
public record KickAccountData(
  @Id @MappedProperty("kick_id") String kickId,
  String username,
  @MappedProperty String recipientId,
  @MappedProperty(type = DataType.UUID) String refreshTokenId
) {}
