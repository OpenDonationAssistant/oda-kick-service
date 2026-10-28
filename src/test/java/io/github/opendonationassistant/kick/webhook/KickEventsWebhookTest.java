package io.github.opendonationassistant.kick.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.kick.account.KickAccount;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.github.opendonationassistant.kick.metrics.KickMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.serde.ObjectMapper;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class KickEventsWebhookTest {

  private static final String FOLLOW_BODY = """
    {
      "broadcaster": {"user_id": "broadcaster-1", "username": "streamer"},
      "follower": {"user_id": "follower-1", "username": "follower"}
    }
    """;

  private static final String REWARD_BODY = """
    {
      "user_input": "song",
      "status": "accepted",
      "reward": {"title": "Song", "id": "reward-1", "cost": 100},
      "redeemer": {"user_id": "redeemer-1", "username": "donor"},
      "broadcaster": {"user_id": "broadcaster-1", "username": "streamer"}
    }
    """;

  private static final String REWARD_BODY_WITHOUT_BROADCASTER = """
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
  private final KickEventsWebhook webhook = new KickEventsWebhook(
    accounts,
    ObjectMapper.getDefault(),
    new KickMetrics(registry)
  );

  @Test
  public void malformedBodyForKnownTypeIsInvalidAndNeverLookedUp() {
    webhook.webhook("channel.followed", "1", "{not-json").join();

    assertEquals(1.0, count("channel.followed", KickMetrics.INVALID));
    verifyNoInteractions(accounts);
  }

  @Test
  public void unknownTypeIsInvalidAndNeverParsedAsReward() {
    // A valid reward payload for an unsubscribed type: parsing it as a reward
    // would query the account repository, which must not happen anymore.
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
    // Guards the null record component NPE: a missing broadcaster used to
    // escape as a 500, which made Kick retry the same delivery forever.
    webhook
      .webhook(
        "channel.reward.redemption.updated",
        "1",
        REWARD_BODY_WITHOUT_BROADCASTER
      )
      .join();

    assertEquals(
      1.0,
      count("channel.reward.redemption.updated", KickMetrics.INVALID)
    );
    verifyNoInteractions(accounts);
  }

  private void accountsReturn(Optional<KickAccount> result) {
    when(accounts.findByKickId("broadcaster-1")).thenReturn(
      CompletableFuture.completedFuture(result)
    );
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
