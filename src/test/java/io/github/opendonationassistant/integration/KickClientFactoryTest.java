package io.github.opendonationassistant.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.integration.KickDataClient.RejectRedemptionsRequest;
import io.github.opendonationassistant.rabbit.TokenRPC;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class KickClientFactoryTest {

  private final KickDataClient dataClient = mock(KickDataClient.class);
  private final TokenRPC tokenRPC = mock(TokenRPC.class);
  private final ExecutorService tokenExecutor =
    Executors.newSingleThreadExecutor();
  private final KickClientFactory factory = new KickClientFactory(
    dataClient,
    tokenRPC,
    tokenExecutor
  );

  @AfterEach
  public void shutdownTokenExecutor() {
    tokenExecutor.shutdownNow();
  }

  @Test
  public void createsClientWithTheResolvedBearerToken() {
    when(tokenRPC.token(new TokenRPC.TokenRequest("rec", "rt-1"))).thenReturn(
      new TokenRPC.TokenResponse("access-token", "ok")
    );

    factory.create("rec", "rt-1").join().deleteReward("reward-1");

    verify(tokenRPC).token(new TokenRPC.TokenRequest("rec", "rt-1"));
    verify(dataClient).deleteReward("Bearer access-token", "reward-1");
  }

  @Test
  public void reusesTheTokenForEveryDelegatedCall() {
    when(tokenRPC.token(any())).thenReturn(
      new TokenRPC.TokenResponse("access-token", "ok")
    );

    var client = factory.create("rec", "rt-1").join();
    client.rejectRedemption("redemption-1");
    client.deleteReward("reward-1");

    verify(tokenRPC, times(1)).token(any());
    verify(dataClient).rejectRedemptions(
      "Bearer access-token",
      new RejectRedemptionsRequest(List.of("redemption-1"))
    );
    verify(dataClient).deleteReward("Bearer access-token", "reward-1");
  }

  @Test
  public void failsWhenTheTokenCannotBeResolved() {
    when(tokenRPC.token(any())).thenReturn(null);

    var thrown = assertThrows(CompletionException.class, () ->
      factory.create("rec", "rt-1").join()
    );

    assertTrue(thrown.getCause() instanceof Problem);
  }
}
