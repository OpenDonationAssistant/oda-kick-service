package io.github.opendonationassistant.kick.listener.handlers;

import io.github.opendonationassistant.events.AbstractMessageHandler;
import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.kick.subscription.SubscriptionsData;
import io.github.opendonationassistant.kick.subscription.SubscriptionsDataRepository;
import io.github.opendonationassistant.kick.subscription.SubscriptionsData.Subscription;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Singleton
public class UnsubscribeEventsHandler
  extends AbstractMessageHandler<
    UnsubscribeEventsHandler.UnsubscribeKickEventsCommand
  > {

  private final KickClient kick;
  private final SubscriptionsDataRepository dataRepository;

  @Inject
  public UnsubscribeEventsHandler(
    ObjectMapper mapper,
    KickClient kick,
    SubscriptionsDataRepository dataRepository
  ) {
    super(mapper);
    this.kick = kick;
    this.dataRepository = dataRepository;
  }

  @Serdeable
  public static record UnsubscribeKickEventsCommand(
    String recipientId,
    String refreshTokenId,
    @Nullable List<String> events
  ) {}

  @Override
  public void handle(UnsubscribeKickEventsCommand message) throws IOException {
    dataRepository
      .findByRecipientIdAndTokenId(
        message.recipientId(),
        message.refreshTokenId()
      )
      .stream()
      .findFirst()
      .map(data -> {
        record LeftAndDeleted(
          String subscriptionId,
          List<Subscription> left,
          List<Subscription> deleted
        ) {}
        if (message.events() == null) {
          return new LeftAndDeleted(data.id(), List.of(), data.events());
        }
        var left = new ArrayList<Subscription>();
        var toDelete = new ArrayList<Subscription>();
        for (Subscription subscription : data.events()) {
          if (message.events().contains(subscription.name())) {
            toDelete.add(subscription);
          } else {
            left.add(subscription);
          }
        }
        return new LeftAndDeleted(data.id(), left, toDelete);
      })
      .ifPresent(leftAndDeleted -> {
        kick
          .unsubscribe(
            message.recipientId(),
            message.refreshTokenId(),
            leftAndDeleted.deleted().stream().map(Subscription::id).toList()
          )
          .thenAccept(response -> {
            if (leftAndDeleted.left().isEmpty()) {
              dataRepository.deleteById(leftAndDeleted.subscriptionId());
            } else {
              dataRepository.update(
                new SubscriptionsData(
                  leftAndDeleted.subscriptionId(),
                  message.recipientId(),
                  message.refreshTokenId(),
                  leftAndDeleted.left()
                )
              );
            }
          });
      });
  }
}
