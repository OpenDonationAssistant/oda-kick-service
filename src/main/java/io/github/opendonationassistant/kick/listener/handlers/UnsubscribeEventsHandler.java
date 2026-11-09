package io.github.opendonationassistant.kick.listener.handlers;

import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.events.AbstractMessageHandler;
import io.github.opendonationassistant.integration.KickClientFactory;
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
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@Singleton
public class UnsubscribeEventsHandler
  extends AbstractMessageHandler<
    UnsubscribeEventsHandler.UnsubscribeKickEventsCommand
  > {

  private final ODALogger log = new ODALogger(this);
  private final KickClientFactory kickFactory;
  private final SubscriptionsDataRepository dataRepository;

  @Inject
  public UnsubscribeEventsHandler(
    ObjectMapper mapper,
    KickClientFactory kickFactory,
    SubscriptionsDataRepository dataRepository
  ) {
    super(mapper);
    this.kickFactory = kickFactory;
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
        kickFactory
          .create(message.recipientId(), message.refreshTokenId())
          .thenCompose(kick ->
            kick.unsubscribe(
              leftAndDeleted.deleted().stream().map(Subscription::id).toList()
            )
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
          })
          .exceptionally(error -> {
            // The unsubscribe future is not joined, so log it instead of losing
            // it: a failed token lookup or Kick call must not be silent.
            log.error(
              "Failed to unsubscribe kick events",
              Map.of(
                "recipientId",
                message.recipientId(),
                "subscriptionId",
                leftAndDeleted.subscriptionId(),
                "error",
                Objects.toString(error.getMessage(), "unknown error")
              )
            );
            return null;
          });
      });
  }
}
