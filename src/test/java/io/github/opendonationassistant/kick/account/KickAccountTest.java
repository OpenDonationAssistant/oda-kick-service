package io.github.opendonationassistant.kick.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageRequest;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageResponse;
import io.github.opendonationassistant.kick.events.KickRewardEvent;
import io.github.opendonationassistant.kick.reward.repository.RewardData;
import io.github.opendonationassistant.kick.reward.repository.RewardDataRepository;
import io.github.opendonationassistant.kick.webhook.KickEventsWebhook.KickRewardPayload;
import io.github.opendonationassistant.kick.webhook.KickEventsWebhook.KickUser;
import io.github.opendonationassistant.kick.webhook.KickEventsWebhook.Reward;
import io.github.opendonationassistant.rabbit.RabbitClient;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class KickAccountTest {

  private final KickClient kick = mock(KickClient.class);
  private final RewardDataRepository rewardRepository = mock(
    RewardDataRepository.class
  );
  private final RabbitClient commandsRabbit = mock(RabbitClient.class);
  private final RabbitClient eventsRabbit = mock(RabbitClient.class);

  private KickAccount account() {
    return account("123");
  }

  private KickAccount account(String kickId) {
    return new KickAccount(
      rewardRepository,
      kick,
      commandsRabbit,
      eventsRabbit,
      new KickAccountData("id-1", kickId, "user", "rec", "rt-1")
    );
  }

  @Test
  public void sendsMessageWithAccountCredentials() {
    when(kick.sendChatMessage(anyString(), anyString(), any())).thenReturn(
      CompletableFuture.completedFuture(
        new DataWrapper<>(new SendChatMessageResponse(true, "msg-1"))
      )
    );

    var response = account().sendMessage("hello").join();

    assertEquals(
      Boolean.TRUE,
      Objects.requireNonNull(response.data()).isSent()
    );
    verify(kick).sendChatMessage(
      "rec",
      "rt-1",
      new SendChatMessageRequest("hello", "user", "123")
    );
  }

  @Test
  public void sendsMessageToTheAccountsOwnChannel() {
    when(kick.sendChatMessage(anyString(), anyString(), any())).thenReturn(
      CompletableFuture.completedFuture(
        new DataWrapper<>(new SendChatMessageResponse(true, "msg-1"))
      )
    );

    account("987654").sendMessage("hello").join();

    verify(kick).sendChatMessage(
      "rec",
      "rt-1",
      new SendChatMessageRequest("hello", "user", "987654")
    );
  }

  @Test
  public void returnsWrapperWhenIsSentIsUnknown() {
    when(kick.sendChatMessage(anyString(), anyString(), any())).thenReturn(
      CompletableFuture.completedFuture(
        new DataWrapper<>(new SendChatMessageResponse(null, null))
      )
    );

    var response = account().sendMessage("hello").join();

    assertNotNull(response);
    assertNotNull(response.data());
  }

  @Test
  public void sendsKickIdVerbatim() {
    // the id is an opaque string on the account: no parsing, no coercion
    when(kick.sendChatMessage(anyString(), anyString(), any())).thenReturn(
      CompletableFuture.completedFuture(
        new DataWrapper<>(new SendChatMessageResponse(true, "msg-1"))
      )
    );

    account("kick-abc").sendMessage("hello").join();

    verify(kick).sendChatMessage(
      "rec",
      "rt-1",
      new SendChatMessageRequest("hello", "user", "kick-abc")
    );
  }

  @Test
  public void sameRedemptionProducesSameEventId() throws IOException {
    givenRewardExists();
    var account = account();

    account.handleReward(payload("water", "accepted"));
    account.handleReward(payload("water", "accepted"));

    var events = capturedRewardEvents(2);
    assertEquals(events.get(0).id(), events.get(1).id());
  }

  @Test
  public void differentRedemptionStatusProducesDifferentEventId()
    throws IOException {
    givenRewardExists();
    var account = account();

    account.handleReward(payload("water", "accepted"));
    account.handleReward(payload("water", "rejected"));

    var events = capturedRewardEvents(2);
    assertNotEquals(events.get(0).id(), events.get(1).id());
  }

  @Test
  public void differentUserInputProducesDifferentEventId() throws IOException {
    givenRewardExists();
    var account = account();

    account.handleReward(payload("water", "accepted"));
    account.handleReward(payload("beer", "accepted"));

    var events = capturedRewardEvents(2);
    assertNotEquals(events.get(0).id(), events.get(1).id());
  }

  @Test
  public void eventCarriesRedemptionDetails() throws IOException {
    givenRewardExists();

    account().handleReward(payload("water", "accepted"));

    var event = capturedRewardEvents(1).get(0);
    assertEquals("rec", event.recipientId());
    assertEquals("viewer", event.username());
    assertEquals("Hydrate", event.rewardTitle());
    assertEquals("reward-1", event.rewardId());
    assertEquals("water", event.input());
    assertEquals("accepted", event.status());
    assertEquals(Integer.valueOf(100), event.cost());
    assertEquals("user-1", event.redeemerId());
    assertNotNull(event.timestamp());
  }

  @Test
  public void publishesMediaCommandOnlyForKnownRewards() throws IOException {
    account().handleReward(payload("water", "accepted"));

    verify(commandsRabbit, times(0)).sendCommand(any());
    verify(eventsRabbit, times(0)).sendEvent(any());
  }

  private void givenRewardExists() {
    when(rewardRepository.findById("reward-1")).thenReturn(
      Optional.of(new RewardData("reward-1", "id-1", "widget-1", "media"))
    );
  }

  private List<KickRewardEvent> capturedRewardEvents(int count)
    throws IOException {
    var captor = ArgumentCaptor.forClass(KickRewardEvent.class);
    verify(eventsRabbit, times(count)).sendEvent(captor.capture());
    return captor.getAllValues();
  }

  private static KickRewardPayload payload(String input, String status) {
    return new KickRewardPayload(
      input,
      status,
      new Reward("Hydrate", "reward-1", 100),
      new KickUser("user-1", "viewer"),
      new KickUser("broadcaster-1", "streamer")
    );
  }
}
