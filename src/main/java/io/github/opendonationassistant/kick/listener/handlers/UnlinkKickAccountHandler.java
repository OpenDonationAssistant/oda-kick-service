package io.github.opendonationassistant.kick.listener.handlers;

import io.github.opendonationassistant.events.AbstractMessageHandler;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;

@Singleton
public class UnlinkKickAccountHandler
  extends AbstractMessageHandler<
    UnlinkKickAccountHandler.UnlinkKickAccount
  > {

  private final KickAccountRepository repository;

  public UnlinkKickAccountHandler(
    ObjectMapper mapper,
    KickAccountRepository repository
  ) {
    super(mapper);
    this.repository = repository;
  }

  @Serdeable
  public static record UnlinkKickAccount(
    String recipientId,
    String refreshTokenId
  ) {}

  @Override
  public void handle(UnlinkKickAccount message) throws IOException {
    repository.delete(message.recipientId(), message.refreshTokenId()).join();
  }
}
