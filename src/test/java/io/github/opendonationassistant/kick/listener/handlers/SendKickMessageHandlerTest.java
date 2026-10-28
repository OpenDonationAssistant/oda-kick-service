package io.github.opendonationassistant.kick.listener.handlers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageRequest;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageResponse;
import io.github.opendonationassistant.kick.account.KickAccount;
import io.github.opendonationassistant.kick.account.KickAccountData;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.github.opendonationassistant.kick.listener.handlers.SendKickMessageHandler.SendKickMessageCommand;
import io.github.opendonationassistant.kick.reward.repository.RewardDataRepository;
import io.github.opendonationassistant.rabbit.RabbitClient;
import io.micronaut.serde.ObjectMapper;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class SendKickMessageHandlerTest {

  private static final String RECIPIENT_ID = "rec";
  private static final String REFRESH_TOKEN_ID = "rt-1";
  private static final String KICK_ID = "123";

  private final KickAccountRepository accounts = mock(
    KickAccountRepository.class
  );
  private final KickClient kick = mock(KickClient.class);
  private final ObjectMapper mapper = ObjectMapper.getDefault();
  private final SendKickMessageHandler handler = new SendKickMessageHandler(
    mapper,
    accounts
  );

  // A real account on purpose: the recipientKickId -> channel mapping lives in
  // KickAccount (sendMessage posts to data().kickId()), so mocking it would hide
  // exactly the behaviour these tests are about.
  private final KickAccount account = new KickAccount(
    mock(RewardDataRepository.class),
    kick,
    mock(RabbitClient.class),
    mock(RabbitClient.class),
    new KickAccountData(
      "id-1",
      KICK_ID,
      "user",
      RECIPIENT_ID,
      REFRESH_TOKEN_ID
    )
  );

  private static SendKickMessageCommand command() {
    return new SendKickMessageCommand(
      RECIPIENT_ID,
      REFRESH_TOKEN_ID,
      KICK_ID,
      "hello"
    );
  }

  private void givenAccount() {
    when(
      accounts.findByRecipientIdAndRefreshTokenId(
        RECIPIENT_ID,
        REFRESH_TOKEN_ID
      )
    ).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
  }

  private void givenResponse(DataWrapper<SendChatMessageResponse> response) {
    when(kick.sendChatMessage(anyString(), anyString(), any())).thenReturn(
      CompletableFuture.completedFuture(response)
    );
  }

  @Test
  public void sendsToOwnChannelWithAccountCredentials() {
    givenAccount();
    givenResponse(
      new DataWrapper<>(new SendChatMessageResponse(Boolean.TRUE, "msg-1"))
    );

    handler.handle(command());

    verify(accounts).findByRecipientIdAndRefreshTokenId(
      RECIPIENT_ID,
      REFRESH_TOKEN_ID
    );
    final var request = ArgumentCaptor.forClass(
      SendChatMessageRequest.class
    );
    verify(kick).sendChatMessage(
      eq(RECIPIENT_ID),
      eq(REFRESH_TOKEN_ID),
      request.capture()
    );
    assertEquals("hello", request.getValue().content());
    assertEquals("user", request.getValue().type());
    // The account's own Kick id, passed through verbatim from data().kickId().
    assertEquals(KICK_ID, request.getValue().broadcasterUserId());
  }

  @Test
  public void doesNotSendWhenAccountNotFound() {
    when(
      accounts.findByRecipientIdAndRefreshTokenId(anyString(), anyString())
    ).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    assertDoesNotThrow(() -> handler.handle(command()));

    verifyNoInteractions(kick);
  }

  @Test
  public void doesNotSendWhenRecipientKickIdDoesNotMatchAccountChannel() {
    givenAccount();

    assertDoesNotThrow(() ->
      handler.handle(
        new SendKickMessageCommand(
          RECIPIENT_ID,
          REFRESH_TOKEN_ID,
          "999",
          "hello"
        )
      )
    );

    verifyNoInteractions(kick);
  }

  @Test
  public void rethrowsClientFailureSoTheMessageStaysUnacked() {
    givenAccount();
    when(kick.sendChatMessage(anyString(), anyString(), any())).thenReturn(
      CompletableFuture.failedFuture(new RuntimeException("boom"))
    );

    final var thrown = assertThrows(RuntimeException.class, () ->
      handler.handle(command())
    );

    assertTrue(Objects.toString(thrown.getMessage(), "").contains("boom"));
  }

  @Test
  public void doesNotRetryWhenKickRejectsTheMessage() {
    givenAccount();
    givenResponse(
      new DataWrapper<>(new SendChatMessageResponse(Boolean.FALSE, "msg-1"))
    );

    assertDoesNotThrow(() -> handler.handle(command()));

    verify(kick, times(1)).sendChatMessage(
      eq(RECIPIENT_ID),
      eq(REFRESH_TOKEN_ID),
      any()
    );
  }

  @Test
  public void doesNotRetryWhenIsSentIsAbsent() {
    givenAccount();
    givenResponse(new DataWrapper<>(new SendChatMessageResponse(null, null)));

    assertDoesNotThrow(() -> handler.handle(command()));

    verify(kick, times(1)).sendChatMessage(
      eq(RECIPIENT_ID),
      eq(REFRESH_TOKEN_ID),
      any()
    );
  }

  @Test
  public void doesNotThrowWhenResponseHasNoData() {
    givenAccount();
    givenResponse(new DataWrapper<SendChatMessageResponse>(null));

    assertDoesNotThrow(() -> handler.handle(command()));

    verify(kick, times(1)).sendChatMessage(
      eq(RECIPIENT_ID),
      eq(REFRESH_TOKEN_ID),
      any()
    );
  }
}
