package io.github.opendonationassistant.kick.listener.handlers;

import com.fasterxml.uuid.Generators;
import io.github.opendonationassistant.events.AbstractMessageHandler;
import io.github.opendonationassistant.kick.account.KickAccount;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class LinkAccountHandler
  extends AbstractMessageHandler<LinkAccountHandler.LinkKickAccount> {

  private final KickAccountRepository repository;

  public LinkAccountHandler(
    ObjectMapper mapper,
    KickAccountRepository repository
  ) {
    super(mapper);
    this.repository = repository;
  }

  @Serdeable
  public static record LinkKickAccount(
    String kickId,
    String username,
    String recipientId,
    String refreshTokenId
  ) {}

  @Override
  public void handle(LinkKickAccount message) throws IOException {
    repository
      .findByRecipientIdAndRefreshTokenId(
        message.recipientId(),
        message.refreshTokenId()
      )
      .thenCompose(existed -> {
        if (existed.isPresent()) {
          return CompletableFuture.completedFuture(null);
        }
        return repository.create(
          new io.github.opendonationassistant.kick.account.KickAccountData(
            Generators.timeBasedEpochGenerator().generate().toString(),
            message.kickId(),
            message.username(),
            message.recipientId(),
            message.refreshTokenId()
          )
        );
      })
      .join();
  }
}
