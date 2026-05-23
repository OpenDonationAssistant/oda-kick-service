package io.github.opendonationassistant.kick.listener.handlers;

import io.github.opendonationassistant.events.AbstractMessageHandler;
import io.github.opendonationassistant.rabbit.RabbitClient;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;

@Singleton
public class SubscribeAllKickEventsHandler
  extends AbstractMessageHandler<
    SubscribeAllKickEventsHandler.SubscribeAllKickEventsCommand
  > {

  private final RabbitClient rabbit;

  public SubscribeAllKickEventsHandler(
    ObjectMapper mapper,
    @Named("commands") RabbitClient rabbit
  ) {
    super(mapper);
    this.rabbit = rabbit;
  }

  @Override
  public void handle(SubscribeAllKickEventsCommand message) throws IOException {
    rabbit.sendCommand(
      new SubscribeEventsHandler.SubscribeKickEventsCommand(
        message.recipientId(),
        message.token(),
        message.refreshTokenId(),
        List.of(
          "channel.followed",
          "channel.subscription.new",
          "channel.subscription.renewal",
          "channel.subscription.gifts",
          "channel.reward.redemption.updated",
          "livestream.status.updated",
          "livestream.metadata.updated",
          "kicks.gifted"
        )
      )
    );
  }

  @Serdeable
  public static record SubscribeAllKickEventsCommand(
    String recipientId,
    String token,
    String refreshTokenId
  ) {}
}
