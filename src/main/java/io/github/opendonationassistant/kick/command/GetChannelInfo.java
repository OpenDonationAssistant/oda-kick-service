package io.github.opendonationassistant.kick.command;

import io.github.opendonationassistant.integration.KickWebClient;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import org.zalando.problem.Problem;

@Controller
public class GetChannelInfo {

  private final KickAccountRepository accountRepository;
  private final KickWebClient kickWebClient;

  @Inject
  public GetChannelInfo(
    KickAccountRepository accountRepository,
    KickWebClient kickWebClient
  ) {
    this.accountRepository = accountRepository;
    this.kickWebClient = kickWebClient;
  }

  @Operation(
    summary = "Get kick channel info",
    description = "Get kick channel info"
  )
  @ApiResponse(
    responseCode = "200",
    description = "OK",
    content = @io.swagger.v3.oas.annotations.media.Content(
      schema = @io.swagger.v3.oas.annotations.media.Schema(
        implementation = ChannelInfo.class
      )
    )
  )
  @Post("/kick/commands/get-channel-info")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<HttpResponse<ChannelInfo>> getChannelInfo(
    @Body GetChannelInfoCommand command
  ) {
    return accountRepository
      .findByRefreshTokenId(command.tokenId())
      .thenCompose(account ->
        account
          .map(a ->
            kickWebClient
              .getChannelInfo(a.data().username())
              .thenApply(info ->
                (HttpResponse<ChannelInfo>) HttpResponse.ok(info)
              )
          )
          .orElseThrow(() ->
            Problem.builder()
              .withTitle("Account not found")
              .withDetail(command.tokenId())
              .build()
          )
      );
  }

  @Serdeable
  public static record GetChannelInfoCommand(String tokenId) {}

  @Serdeable
  public static record ChannelInfo(Chatroom chatroom) {}

  @Serdeable
  public static record Chatroom(String id) {}
}
