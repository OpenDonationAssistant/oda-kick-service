package io.github.opendonationassistant.kick.reward.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickClientFactory;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.FailedRedemption;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageRequest;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageResponse;
import io.github.opendonationassistant.integration.media.MediaRPC;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.zalando.problem.Problem;

public class KickRewardTest {

  private static final RewardData DATA = new RewardData(
    "reward-1",
    "id-1",
    "widget-1",
    "media"
  );
  private static final String RECIPIENT_ID = "rec";
  private static final String REFRESH_TOKEN_ID = "rt-1";
  private static final String KICK_ID = "123";

  private MediaRPC media;
  private KickClient kick;
  private KickClientFactory kickFactory;
  private KickReward reward;

  @BeforeEach
  public void setUp() {
    media = mock(MediaRPC.class);
    kick = mock(KickClient.class);
    kickFactory = mock(KickClientFactory.class);
    when(kickFactory.create(RECIPIENT_ID, REFRESH_TOKEN_ID)).thenReturn(
      CompletableFuture.completedFuture(kick)
    );
    reward = new KickReward(
      DATA,
      RECIPIENT_ID,
      REFRESH_TOKEN_ID,
      KICK_ID,
      media,
      kickFactory
    );
  }

  @Test
  public void exposesItsDataAndRecipient() {
    assertEquals(DATA, reward.data());
    assertEquals(RECIPIENT_ID, reward.recipientId());
  }

  @Test
  public void usesTheRedemptionIdAsMediaId() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse("redemption-1", "added")
      )
    );

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    verify(media).add(
      new MediaRPC.AddMediaRequest(
        "redemption-1",
        "water",
        "viewer",
        RECIPIENT_ID,
        "kick"
      )
    );
    verifyNoInteractions(kick);
  }

  @Test
  public void resolvesTheAccountTokenOnlyWhenAKickCallIsNeeded() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    givenChatMessageSent();
    givenRefundSucceeds();

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate").join();

    verify(kickFactory).create(RECIPIENT_ID, REFRESH_TOKEN_ID);
    verify(kick).rejectRedemption("redemption-1");
  }

  @Test
  public void doesNotFailWhenTheTokenCannotBeResolved() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    when(kickFactory.create(anyString(), anyString())).thenReturn(
      CompletableFuture.failedFuture(
        Problem.builder().withTitle("Can't obtain access token").build()
      )
    );

    var result = reward.addMedia(
      "redemption-1",
      "water",
      "viewer",
      "Hydrate"
    );

    assertDoesNotThrow(() -> result.join());
    verify(kick, never()).rejectRedemption(any());
    verify(kick, never()).sendChatMessage(any());
  }

  @Test
  public void usesTheSameMediaIdForRedeliveredRedemption() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse("redemption-1", "added")
      )
    );

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");
    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    var captor = ArgumentCaptor.forClass(MediaRPC.AddMediaRequest.class);
    verify(media, times(2)).add(captor.capture());
    assertEquals(
      captor.getAllValues().get(0).id(),
      captor.getAllValues().get(1).id()
    );
  }

  @Test
  public void usesDifferentMediaIdsForDifferentRedemptions() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse("redemption-1", "added")
      )
    );

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");
    reward.addMedia("redemption-2", "water", "viewer", "Hydrate");

    var captor = ArgumentCaptor.forClass(MediaRPC.AddMediaRequest.class);
    verify(media, times(2)).add(captor.capture());
    assertNotEquals(
      captor.getAllValues().get(0).id(),
      captor.getAllValues().get(1).id()
    );
  }

  @Test
  public void refundsAndNotifiesChatWhenMediaWasRejected() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    givenChatMessageSent();
    givenRefundSucceeds();

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    var message = capturedChatMessage();
    assertTrue(message.contains("@viewer"));
    assertTrue(message.contains("Hydrate"));
    assertTrue(message.contains("unsupported url"));
    assertTrue(message.contains("Баллы за награду возвращены"));
    verify(kick).rejectRedemption("redemption-1");
  }

  @Test
  public void sendsTheFailureMessageToTheAccountsChannel() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    givenChatMessageSent();
    givenRefundSucceeds();

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    var request = capturedChatRequest();
    assertEquals("user", request.type());
    assertEquals(KICK_ID, request.broadcasterUserId());
  }

  @Test
  public void hidesInternalFailuresFromTheViewer() {
    when(media.add(any())).thenThrow(new RuntimeException("rpc timeout"));
    givenChatMessageSent();
    givenRefundSucceeds();

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    var message = capturedChatMessage();
    assertTrue(message.contains("@viewer"));
    assertTrue(message.contains("Hydrate"));
    assertFalse(message.contains("rpc timeout"));
    assertTrue(message.contains("Баллы за награду возвращены"));
    verify(kick).rejectRedemption("redemption-1");
  }

  @Test
  public void tellsTheViewerWhenTheRedemptionWasAlreadyResolved() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    givenChatMessageSent();
    givenRefund(
      new DataWrapper<>(
        List.of(new FailedRedemption("redemption-1", "NOT_PENDING"))
      )
    );

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    var message = capturedChatMessage();
    assertTrue(message.contains("уже была обработана"));
    assertFalse(message.contains("вернуть автоматически не удалось"));
  }

  @Test
  public void reportsWhenThePointsCouldNotBeReturned() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    givenChatMessageSent();
    givenRefund(
      new DataWrapper<>(
        List.of(new FailedRedemption("redemption-1", "UNKNOWN"))
      )
    );

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    assertTrue(
      capturedChatMessage()
        .contains("Баллы вернуть автоматически не удалось")
    );
  }

  @Test
  public void treatsARejectResponseWithoutDataAsFailure() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    givenChatMessageSent();
    givenRefund(new DataWrapper<>(null));

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    assertTrue(
      capturedChatMessage()
        .contains("Баллы вернуть автоматически не удалось")
    );
  }

  @Test
  public void doesNotFailWhenTheRefundFutureFails() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    givenChatMessageSent();
    when(kick.rejectRedemption(any())).thenReturn(
      CompletableFuture.failedFuture(new RuntimeException("kick timeout"))
    );

    assertDoesNotThrow(() ->
      reward.addMedia("redemption-1", "water", "viewer", "Hydrate")
    );

    assertTrue(
      capturedChatMessage()
        .contains("Баллы вернуть автоматически не удалось")
    );
  }

  @Test
  public void doesNotFailWhenTheRefundThrows() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    givenChatMessageSent();
    when(kick.rejectRedemption(any())).thenThrow(
      new RuntimeException("kick api is down")
    );

    assertDoesNotThrow(() ->
      reward.addMedia("redemption-1", "water", "viewer", "Hydrate")
    );

    assertTrue(
      capturedChatMessage()
        .contains("Баллы вернуть автоматически не удалось")
    );
  }

  @Test
  public void doesNotFailWhenTheChatMessageFails() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "unsupported url")
      )
    );
    givenRefundSucceeds();
    when(kick.sendChatMessage(any())).thenReturn(
      CompletableFuture.failedFuture(new RuntimeException("chat is down"))
    );

    assertDoesNotThrow(() ->
      reward.addMedia("redemption-1", "water", "viewer", "Hydrate")
    );

    verify(kick).rejectRedemption("redemption-1");
  }

  @Test
  public void skipsTheMediaRequestWithoutARedemptionId() {
    givenChatMessageSent();

    reward.addMedia(null, "water", "viewer", "Hydrate");

    verifyNoInteractions(media);
    verify(kick, never()).rejectRedemption(any());
    var message = capturedChatMessage();
    assertTrue(message.contains("@viewer"));
    assertTrue(message.contains("Hydrate"));
    assertTrue(message.contains("Баллы вернуть автоматически не удалось"));
  }

  @Test
  public void skipsTheMediaRequestForABlankRedemptionId() {
    givenChatMessageSent();

    reward.addMedia("   ", "water", "viewer", "Hydrate");

    verifyNoInteractions(media);
    verify(kick, never()).rejectRedemption(any());
    assertTrue(capturedChatMessage().contains("@viewer"));
  }

  @Test
  public void treatsANullResponseAsARejectedMedia() {
    when(media.add(any())).thenReturn(CompletableFuture.completedFuture(null));
    givenChatMessageSent();
    givenRefundSucceeds();

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    assertTrue(
      capturedChatMessage().contains("Баллы за награду возвращены")
    );
    verify(kick).rejectRedemption("redemption-1");
  }

  @Test
  public void omitsABlankFailureReason() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "  ")
      )
    );
    givenChatMessageSent();
    givenRefundSucceeds();

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    var message = capturedChatMessage();
    assertTrue(message.contains("»."));
    assertFalse(message.contains("»:"));
  }

  @Test
  public void truncatesALongFailureReason() {
    when(media.add(any())).thenReturn(
      CompletableFuture.completedFuture(
        new MediaRPC.AddMediaResponse(null, "ошибка ".repeat(100))
      )
    );
    givenChatMessageSent();
    givenRefundSucceeds();

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    var message = capturedChatMessage();
    assertTrue(
      message.contains("ошибка ".repeat(100).substring(0, 150) + "…")
    );
  }

  @Test
  public void completesOnlyWhenTheMediaReplyArrives() {
    var reply = new CompletableFuture<MediaRPC.AddMediaResponse>();
    when(media.add(any())).thenReturn(reply);

    var result = reward.addMedia(
      "redemption-1",
      "water",
      "viewer",
      "Hydrate"
    );

    assertFalse(result.isDone());
    reply.complete(new MediaRPC.AddMediaResponse("redemption-1", "added"));
    assertTrue(result.isDone());
    verifyNoInteractions(kick);
  }

  @Test
  public void refundsWhenTheMediaFutureFails() {
    when(media.add(any())).thenReturn(
      CompletableFuture.failedFuture(new RuntimeException("rpc timeout"))
    );
    givenChatMessageSent();
    givenRefundSucceeds();

    reward.addMedia("redemption-1", "water", "viewer", "Hydrate");

    var message = capturedChatMessage();
    assertFalse(message.contains("rpc timeout"));
    assertTrue(message.contains("Баллы за награду возвращены"));
    verify(kick).rejectRedemption("redemption-1");
  }

  private void givenChatMessageSent() {
    when(kick.sendChatMessage(any())).thenReturn(
      CompletableFuture.completedFuture(
        new DataWrapper<>(new SendChatMessageResponse(true, "m-1"))
      )
    );
  }

  private void givenRefundSucceeds() {
    givenRefund(new DataWrapper<>(List.<FailedRedemption>of()));
  }

  private void givenRefund(DataWrapper<List<FailedRedemption>> response) {
    when(kick.rejectRedemption(any())).thenReturn(
      CompletableFuture.completedFuture(response)
    );
  }

  private SendChatMessageRequest capturedChatRequest() {
    var captor = ArgumentCaptor.forClass(SendChatMessageRequest.class);
    verify(kick).sendChatMessage(captor.capture());
    return captor.getValue();
  }

  private String capturedChatMessage() {
    return capturedChatRequest().content();
  }
}
