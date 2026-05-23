package io.github.opendonationassistant.kick.reward.commands;

import io.github.opendonationassistant.commons.micronaut.BaseController;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.util.concurrent.CompletableFuture;

@Controller
public class CreateKickReward extends BaseController {

  @Post("/kick/commands/create-reward")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<HttpResponse<Void>> createReward(
    Authentication auth,
    @Body CreateKickRewardCommand command
  ) {

  }

  @Serdeable
  public record CreateKickRewardCommand(String name, String description) {}
}
