package io.github.opendonationassistant.kick.listener.handlers;

import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.events.AbstractMessageHandler;
import io.github.opendonationassistant.kick.account.KickAccount;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Objects;

/**
 * Executes {@code SendKickMessageCommand} by posting a chat message through the
 * official Kick public API ({@code POST /public/v1/chat}). The request requires
 * the Kick {@code chat:write} scope; without it Kick answers {@code 403}.
 *
 * <p>The account is looked up by {@code recipientId} <b>and</b>
 * {@code senderRefreshTokenId}, and the loaded account's own Kick channel is
 * compared with {@code recipientKickId}, so a command can neither send with
 * another recipient's token nor post into a foreign channel.
 *
 * <p><b>Retry semantics:</b> {@code MessageProcessor} acknowledges the RabbitMQ
 * message only when {@link #handle(SendKickMessageCommand)} returns normally. Any
 * runtime failure (token RPC unavailable, Kick API error, ...) is therefore
 * logged and rethrown: the message stays unacknowledged and is redelivered
 * instead of being silently dropped even though nothing was sent. A business
 * rejection ({@code 200} with {@code is_sent: false}) is <i>not</i> retryable and
 * only produces a warning.
 *
 * <p>// TODO: existing linked accounts must re-authorize to grant chat:write
 * (scope set lives in oda-recipient-service); Kick returns 403 otherwise.
 */
@Singleton
public class SendKickMessageHandler
  extends AbstractMessageHandler<
    SendKickMessageHandler.SendKickMessageCommand
  > {

  private final ODALogger log = new ODALogger(this);
  private final KickAccountRepository accounts;

  @Inject
  public SendKickMessageHandler(
    ObjectMapper mapper,
    KickAccountRepository accounts
  ) {
    super(mapper);
    this.accounts = accounts;
  }

  @Override
  public void handle(SendKickMessageCommand message) {
    try {
      accounts
        .findByRecipientIdAndRefreshTokenId(
          message.recipientId(),
          message.senderRefreshTokenId()
        )
        .join()
        .ifPresentOrElse(
          account -> send(message, account),
          () ->
            log.warn(
              "Kick account not found",
              Map.of(
                "recipientId",
                message.recipientId(),
                "senderRefreshTokenId",
                message.senderRefreshTokenId()
              )
            )
        );
    } catch (RuntimeException e) {
      // Every value here is Objects.toString'ed: Map.of throws on nulls and a
      // null field (e.g. a publisher that renamed a component) must not turn
      // the failure path itself into an NPE that keeps redelivering forever.
      log.error(
        "Failed to execute SendKickMessageCommand",
        Map.of(
          "recipientId",
          Objects.toString(message.recipientId(), ""),
          "recipientKickId",
          Objects.toString(message.recipientKickId(), ""),
          "message",
          Objects.toString(message.message(), ""),
          "error",
          Objects.toString(e.getMessage(), "")
        )
      );
      // Let MessageProcessor leave the message unacknowledged (broker retry).
      throw e;
    }
  }

  private void send(SendKickMessageCommand message, KickAccount account) {
    final var accountKickId = account.data().kickId();
    if (!accountKickId.equals(message.recipientKickId())) {
      log.error(
        "Kick channel does not belong to the recipient, refusing to send",
        Map.of(
          "recipientId",
          Objects.toString(message.recipientId(), ""),
          "accountKickId",
          accountKickId,
          "recipientKickId",
          Objects.toString(message.recipientKickId(), "")
        )
      );
      return;
    }

    // The account always posts into its own channel, see KickAccount.sendMessage.
    final var data = account.sendMessage(message.message()).join().data();
    if (data == null || !Boolean.TRUE.equals(data.isSent())) {
      log.warn(
        "Kick message was not sent",
        Map.of(
          "recipientId",
          Objects.toString(message.recipientId(), ""),
          "recipientKickId",
          Objects.toString(message.recipientKickId(), "")
        )
      );
      return;
    }
    final var messageId = data.messageId();
    if (messageId != null) {
      log.info(
        "Kick message sent",
        Map.of(
          "recipientId",
          message.recipientId(),
          "recipientKickId",
          message.recipientKickId(),
          "messageId",
          messageId
        )
      );
    }
  }

  @Serdeable
  public static record SendKickMessageCommand(
    String recipientId,
    String senderRefreshTokenId,
    String recipientKickId,
    String message
  ) {}
}
