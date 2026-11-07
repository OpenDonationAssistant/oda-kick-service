package io.github.opendonationassistant.integration;

import io.github.opendonationassistant.rabbit.TokenRPC;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.zalando.problem.Problem;

@Singleton
public class KickClientFactory {

  private final KickDataClient client;
  private final TokenRPC tokenRPC;

  @Inject
  public KickClientFactory(KickDataClient client, TokenRPC tokenRPC) {
    this.client = client;
    this.tokenRPC = tokenRPC;
  }

  public KickClient create(String recipientId, String refreshTokenId) {
    return new KickClient(client, token(recipientId, refreshTokenId));
  }

  private String token(String recipientId, String refreshTokenId) {
    return Optional.ofNullable(
      tokenRPC.token(new TokenRPC.TokenRequest(recipientId, refreshTokenId))
    )
      .map(TokenRPC.TokenResponse::token)
      .map(t -> "Bearer " + t)
      .orElseThrow(() ->
        Problem.builder().withTitle("Can't obtain access token").build()
      );
  }
}
