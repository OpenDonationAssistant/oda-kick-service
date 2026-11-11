package io.github.opendonationassistant.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.integration.KickDataClient.RejectRedemptionsRequest;
import io.github.opendonationassistant.rabbit.AsyncTokenRPC;
import io.github.opendonationassistant.rabbit.TokenRPC.TokenRequest;
import io.github.opendonationassistant.rabbit.TokenRPC.TokenResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class KickClientFactoryTest {

  private static final TokenRequest REQUEST = new TokenRequest("rec", "rt-1");

  private final KickDataClient dataClient = mock(KickDataClient.class);
  private final AsyncTokenRPC asyncTokenRPC = mock(AsyncTokenRPC.class);
  private final KickClientFactory factory = new KickClientFactory(
    dataClient,
    asyncTokenRPC
  );

  @Test
  public void createsClientWithTheResolvedBearerToken() {
    when(asyncTokenRPC.token(REQUEST)).thenReturn(
      CompletableFuture.completedFuture(new TokenResponse("access-token", "ok"))
    );

    factory.create("rec", "rt-1").join().deleteReward("reward-1");

    verify(asyncTokenRPC).token(REQUEST);
    verify(dataClient).deleteReward("Bearer access-token", "reward-1");
  }

  @Test
  public void reusesTheTokenForEveryDelegatedCall() {
    when(asyncTokenRPC.token(any())).thenReturn(
      CompletableFuture.completedFuture(new TokenResponse("access-token", "ok"))
    );

    var client = factory.create("rec", "rt-1").join();
    client.rejectRedemption("redemption-1");
    client.deleteReward("reward-1");

    verify(asyncTokenRPC, times(1)).token(any());
    verify(dataClient).rejectRedemptions(
      "Bearer access-token",
      new RejectRedemptionsRequest(List.of("redemption-1"))
    );
    verify(dataClient).deleteReward("Bearer access-token", "reward-1");
  }

  @Test
  public void failsWhenTheTokenResponseIsMissing() {
    when(asyncTokenRPC.token(any())).thenReturn(
      CompletableFuture.completedFuture(null)
    );

    assertFailsWithoutAccessToken(factory.create("rec", "rt-1"));
  }

  @Test
  public void failsWhenTheTokenResponseHasNoToken() {
    when(asyncTokenRPC.token(any())).thenReturn(
      CompletableFuture.completedFuture(new TokenResponse(null, "no token"))
    );

    assertFailsWithoutAccessToken(factory.create("rec", "rt-1"));
  }

  private static void assertFailsWithoutAccessToken(
    CompletableFuture<KickClient> created
  ) {
    var thrown = assertThrows(CompletionException.class, created::join);

    var problem = assertInstanceOf(Problem.class, thrown.getCause());
    assertEquals("Can't obtain access token", problem.getTitle());
  }
}
