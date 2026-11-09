package io.github.opendonationassistant.kick.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickClientFactory;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageRequest;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageResponse;
import io.github.opendonationassistant.integration.media.MediaRPC;
import io.github.opendonationassistant.kick.reward.repository.RewardData;
import io.github.opendonationassistant.kick.reward.repository.RewardDataRepository;
import io.github.opendonationassistant.rabbit.RabbitClient;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KickAccountTest {

  private final KickClient kick = mock(KickClient.class);
  private final KickClientFactory kickFactory = mock(KickClientFactory.class);
  private final RewardDataRepository rewardRepository = mock(
    RewardDataRepository.class
  );
  private final MediaRPC media = mock(MediaRPC.class);
  private final RabbitClient eventsRabbit = mock(RabbitClient.class);

  @BeforeEach
  public void stubKickApi() {
    when(kickFactory.create(anyString(), anyString())).thenReturn(
      CompletableFuture.completedFuture(kick)
    );
    when(kick.sendChatMessage(any())).thenReturn(
      CompletableFuture.completedFuture(
        new DataWrapper<>(new SendChatMessageResponse(true, "msg-1"))
      )
    );
  }

  private KickAccount account() {
    return account("123");
  }

  private KickAccount account(String kickId) {
    return account(kickId, "rec");
  }

  private KickAccount account(String kickId, String recipientId) {
    return new KickAccount(
      rewardRepository,
      kickFactory,
      media,
      eventsRabbit,
      new KickAccountData("id-1", kickId, "user", recipientId, "rt-1")
    );
  }

  @Test
  public void sendsMessageWithAccountCredentials() {
    var response = account().sendMessage("hello").join();

    assertEquals(
      Boolean.TRUE,
      Objects.requireNonNull(response.data()).isSent()
    );
    verify(kick).sendChatMessage(
      new SendChatMessageRequest("hello", "user", "123")
    );
  }

  @Test
  public void obtainsTheClientForTheAccountCredentials() {
    account().sendMessage("hello").join();

    verify(kickFactory).create("rec", "rt-1");
  }

  @Test
  public void sendsMessageToTheAccountsOwnChannel() {
    account("987654").sendMessage("hello").join();

    verify(kick).sendChatMessage(
      new SendChatMessageRequest("hello", "user", "987654")
    );
  }

  @Test
  public void returnsWrapperWhenIsSentIsUnknown() {
    when(kick.sendChatMessage(any())).thenReturn(
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
    account("kick-abc").sendMessage("hello").join();

    verify(kick).sendChatMessage(
      new SendChatMessageRequest("hello", "user", "kick-abc")
    );
  }

  @Test
  public void linksTheRewardToTheAccount() {
    givenRewardExists();

    var reward = account().reward("reward-1").orElseThrow();

    assertEquals(
      new RewardData("reward-1", "id-1", "widget-1", "media"),
      reward.data()
    );
    assertEquals("rec", reward.recipientId());
  }

  @Test
  public void returnsNoRewardForUnknownId() {
    assertTrue(account().reward("unknown").isEmpty());
  }

  @Test
  public void linksTheRewardsOfTheWidget() {
    when(rewardRepository.findByWidgetId("widget-1")).thenReturn(
      List.of(new RewardData("reward-1", "id-1", "widget-1", "media"))
    );

    var rewards = account().getRewardsForWidget("widget-1").toList();

    assertEquals(1, rewards.size());
    assertEquals("reward-1", rewards.get(0).data().id());
    assertEquals("rec", rewards.get(0).recipientId());
  }

  private void givenRewardExists() {
    when(rewardRepository.findById("reward-1")).thenReturn(
      Optional.of(new RewardData("reward-1", "id-1", "widget-1", "media"))
    );
  }
}
