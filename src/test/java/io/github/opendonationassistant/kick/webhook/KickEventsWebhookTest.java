package io.github.opendonationassistant.kick.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.kick.account.KickAccount;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.github.opendonationassistant.kick.events.KickRewardEvent;
import io.github.opendonationassistant.kick.metrics.KickMetrics;
import io.github.opendonationassistant.kick.reward.repository.KickReward;
import io.github.opendonationassistant.rabbit.RabbitClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@MicronautTest(transactional = false)
public class KickEventsWebhookTest {

  private static final String REWARD_TYPE = "channel.reward.redemption.updated";

  private static final String FOLLOW_BODY =
    """
    {
      "broadcaster": {"user_id": "broadcaster-1", "username": "streamer"},
      "follower": {"user_id": "follower-1", "username": "follower"}
    }
    """;

  private static final String REWARD_BODY = rewardBody("accepted", "song");
  private static final String REWARD_BODY_PENDING = rewardBody("pending", "song");
  private static final String REWARD_BODY_REJECTED = rewardBody("rejected", "song");
  private static final String REWARD_BODY_OTHER_INPUT = rewardBody(
    "accepted",
    "beer"
  );
  private static final String REWARD_BODY_OTHER_REDEMPTION = rewardBody(
    "accepted",
    "song",
    "redemption-2"
  );

  private static final String REWARD_BODY_PENDING_WITHOUT_ID =
    """
    {
      "user_input": "song",
      "status": "pending",
      "reward": {"title": "Song", "id": "reward-1", "cost": 100},
      "redeemer": {"user_id": "redeemer-1", "username": "donor"},
      "broadcaster": {"user_id": "broadcaster-1", "username": "streamer"}
    }
    """;

  private static final String REWARD_BODY_WITHOUT_BROADCASTER =
    """
    {
      "user_input": "song",
      "status": "accepted",
      "reward": {"title": "Song", "id": "reward-1", "cost": 100},
      "redeemer": {"user_id": "redeemer-1", "username": "donor"}
    }
    """;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final KickAccountRepository accounts = mock(
    KickAccountRepository.class
  );
  private final KickAccount account = mock(KickAccount.class);
  private final KickReward reward = mock(KickReward.class);
  private final RabbitClient eventsRabbit = mock(RabbitClient.class);

  @Inject
  ObjectMapper mapper;

  private KickEventsWebhook webhook;

  @BeforeEach
  public void setUp() {
    webhook = new KickEventsWebhook(
      accounts,
      mapper,
      new KickMetrics(registry),
      eventsRabbit
    );
  }

  @Test
  public void malformedBodyForKnownTypeIsInvalidAndNeverLookedUp() {
    webhook.webhook("channel.followed", "1", "{not-json").join();

    assertEquals(1.0, count("channel.followed", KickMetrics.INVALID));
    verifyNoInteractions(accounts);
  }

  @Test
  public void unknownTypeIsInvalidAndNeverParsedAsReward() {
    webhook.webhook("chat.message.sent", "1", REWARD_BODY).join();

    assertEquals(1.0, count(KickMetrics.UNKNOWN, KickMetrics.INVALID));
    assertEquals(0.0, count(KickMetrics.UNKNOWN, KickMetrics.NO_ACCOUNT));
    assertFalse(
      hasSeries("chat.message.sent"),
      "unsubscribed types must be bounded to the unknown series"
    );
    verifyNoInteractions(accounts);
  }

  @Test
  public void knownTypeWithoutAccountIsCountedAsNoAccount() {
    accountsReturn(Optional.empty());

    webhook.webhook("channel.followed", "1", FOLLOW_BODY).join();

    verify(accounts).findByKickId("broadcaster-1");
    assertEquals(1.0, count("channel.followed", KickMetrics.NO_ACCOUNT));
    verify(account, never()).handleFollow(anyString());
  }

  @Test
  public void knownTypeWithAccountDispatchesTheEvent() {
    accountsReturn(Optional.of(account));

    webhook.webhook("channel.followed", "1", FOLLOW_BODY).join();

    verify(accounts).findByKickId("broadcaster-1");
    verify(account).handleFollow("follower");
    assertEquals(1.0, count("channel.followed", KickMetrics.RECEIVED));
    assertEquals(0.0, count("channel.followed", KickMetrics.NO_ACCOUNT));
  }

  @Test
  public void rewardWithoutBroadcasterIsInvalidAndNeverLookedUp() {
    webhook
      .webhook(REWARD_TYPE, "1", REWARD_BODY_WITHOUT_BROADCASTER)
      .join();

    assertEquals(1.0, count(REWARD_TYPE, KickMetrics.INVALID));
    verifyNoInteractions(accounts);
  }

  @Test
  public void rewardIsLookedUpByIdAndAskedToAddMedia() throws IOException {
    accountsReturn(Optional.of(account));
    givenReward();

    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY_PENDING).join();

    verify(account).reward("reward-1");
    verify(reward).addMedia("redemption-1", "song", "donor", "Song");
    assertEquals("pending", capturedRewardEvent().status());
  }

  @Test
  public void addsMediaOnlyForPendingRedemptions() throws IOException {
    accountsReturn(Optional.of(account));
    givenReward();

    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY).join();
    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY_REJECTED).join();

    verify(reward, never()).addMedia(any(), any(), any(), any());
    var events = capturedRewardEvents(2);
    assertEquals("accepted", events.get(0).status());
    assertEquals("rejected", events.get(1).status());
  }

  @Test
  public void passesAMissingRedemptionIdToTheReward() {
    accountsReturn(Optional.of(account));
    givenReward();

    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY_PENDING_WITHOUT_ID).join();

    verify(reward).addMedia(
      isNull(),
      anyString(),
      anyString(),
      anyString()
    );
  }

  @Test
  public void brokerFailureDoesNotFailTheWebhook() throws IOException {
    accountsReturn(Optional.of(account));
    givenReward();
    doThrow(new IOException("broker down"))
      .when(eventsRabbit)
      .sendEvent(any());

    assertDoesNotThrow(() ->
      webhook.webhook(REWARD_TYPE, "1", REWARD_BODY).join()
    );

    verify(reward, never()).addMedia(any(), any(), any(), any());
  }

  @Test
  public void unknownRewardIsIgnored() {
    accountsReturn(Optional.of(account));
    when(account.reward("reward-1")).thenReturn(Optional.empty());

    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY).join();

    verifyNoInteractions(reward);
    verifyNoInteractions(eventsRabbit);
  }

  @Test
  public void rewardEventCarriesTheRedemptionDetails() throws IOException {
    accountsReturn(Optional.of(account));
    givenReward();

    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY).join();

    var event = capturedRewardEvent();
    assertEquals("rec", event.recipientId());
    assertEquals("donor", event.username());
    assertEquals("Song", event.rewardTitle());
    assertEquals("reward-1", event.rewardId());
    assertEquals("song", event.input());
    assertEquals("accepted", event.status());
    assertEquals(Integer.valueOf(100), event.cost());
    assertEquals("redeemer-1", event.redeemerId());
    assertNotNull(event.id());
    assertNotNull(event.timestamp());
  }

  @Test
  public void sameRedemptionProducesSameEventId() throws IOException {
    accountsReturn(Optional.of(account));
    givenReward();

    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY).join();
    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY).join();

    var events = capturedRewardEvents(2);
    assertEquals(events.get(0).id(), events.get(1).id());
  }

  @Test
  public void differentRedemptionStatusProducesDifferentEventId() throws IOException {
    accountsReturn(Optional.of(account));
    givenReward();

    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY).join();
    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY_REJECTED).join();

    var events = capturedRewardEvents(2);
    assertNotEquals(events.get(0).id(), events.get(1).id());
  }

  @Test
  public void differentUserInputProducesDifferentEventId() throws IOException {
    accountsReturn(Optional.of(account));
    givenReward();

    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY).join();
    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY_OTHER_INPUT).join();

    var events = capturedRewardEvents(2);
    assertNotEquals(events.get(0).id(), events.get(1).id());
  }

  @Test
  public void differentRedemptionsProduceDifferentEventIds()
    throws IOException {
    accountsReturn(Optional.of(account));
    givenReward();

    webhook.webhook(REWARD_TYPE, "1", REWARD_BODY).join();
    webhook
      .webhook(REWARD_TYPE, "1", REWARD_BODY_OTHER_REDEMPTION)
      .join();

    var events = capturedRewardEvents(2);
    assertNotEquals(events.get(0).id(), events.get(1).id());
  }

  private void givenReward() {
    when(account.reward("reward-1")).thenReturn(Optional.of(reward));
    when(reward.recipientId()).thenReturn("rec");
    when(reward.addMedia(any(), any(), any(), any())).thenReturn(
      CompletableFuture.completedFuture(null)
    );
  }

  private KickRewardEvent capturedRewardEvent() throws IOException {
    return capturedRewardEvents(1).get(0);
  }

  private List<KickRewardEvent> capturedRewardEvents(int count)
    throws IOException {
    var captor = ArgumentCaptor.forClass(KickRewardEvent.class);
    verify(eventsRabbit, times(count)).sendEvent(captor.capture());
    return captor.getAllValues();
  }

  private void accountsReturn(Optional<KickAccount> result) {
    when(accounts.findByKickId("broadcaster-1")).thenReturn(
      CompletableFuture.completedFuture(result)
    );
  }

  private static String rewardBody(String status, String input) {
    return rewardBody(status, input, "redemption-1");
  }

  private static String rewardBody(
    String status,
    String input,
    String redemptionId
  ) {
    return """
    {
      "id": "%s",
      "user_input": "%s",
      "status": "%s",
      "reward": {"title": "Song", "id": "reward-1", "cost": 100},
      "redeemer": {"user_id": "redeemer-1", "username": "donor"},
      "broadcaster": {"user_id": "broadcaster-1", "username": "streamer"}
    }
    """.formatted(redemptionId, input, status);
  }

  private double count(String type, String outcome) {
    var counter = registry
      .find(KickMetrics.EVENTS_METRIC_NAME)
      .tag(KickMetrics.TYPE_TAG, type)
      .tag(KickMetrics.OUTCOME_TAG, outcome)
      .counter();
    return counter == null ? 0.0 : counter.count();
  }

  private boolean hasSeries(String type) {
    var counter = registry
      .find(KickMetrics.EVENTS_METRIC_NAME)
      .tag(KickMetrics.TYPE_TAG, type)
      .counter();
    return counter != null;
  }
}
