package io.github.opendonationassistant.kick.listener.handlers;

import com.fasterxml.uuid.Generators;
import io.github.opendonationassistant.events.AbstractMessageHandler;
import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient.CreatedSubscription;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.EventSubscription;
import io.github.opendonationassistant.integration.KickDataClient.SubscriptionRequest;
import io.github.opendonationassistant.kick.repository.SubscriptionsData;
import io.github.opendonationassistant.kick.repository.SubscriptionsData.Subscription;
import io.github.opendonationassistant.kick.repository.SubscriptionsDataRepository;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Singleton
public class SubscribeEventsHandler
  extends AbstractMessageHandler<
    SubscribeEventsHandler.SubscribeKickEventsCommand
  > {

  private final KickClient kick;
  private final SubscriptionsDataRepository dataRepository;

  public SubscribeEventsHandler(
    ObjectMapper mapper,
    KickClient kick,
    SubscriptionsDataRepository dataRepository
  ) {
    super(mapper);
    this.kick = kick;
    this.dataRepository = dataRepository;
  }

  @Override
  public void handle(SubscribeKickEventsCommand message) throws IOException {
    kick
      .subscribe(
        message.recipientId(),
        message.refreshTokenId(),
        new SubscriptionRequest(
          message
            .events()
            .stream()
            .map(name -> new EventSubscription(name, 1))
            .toList(),
          "webhook"
        )
      )
      .thenAccept(response -> handleResponse(message, response))
      .join();
  }

  private void handleResponse(
    SubscribeKickEventsCommand message,
    DataWrapper<List<CreatedSubscription>> response
  ) {
    final List<Subscription> created = response
      .data()
      .stream()
      .map(it ->
        new SubscriptionsData.Subscription(it.id(), it.name(), it.version())
      )
      .toList();
    final Optional<SubscriptionsData> existed = dataRepository
      .findByRecipientIdAndTokenId(
        message.recipientId(),
        message.refreshTokenId()
      )
      .stream()
      .findFirst();
    existed.ifPresentOrElse(
      it -> {
        final List<Subscription> updated = Stream.concat(
          it.events().stream(),
          created.stream()
        )
          .distinct()
          .toList();
        dataRepository.update(
          new SubscriptionsData(
            it.id(),
            message.recipientId(),
            message.refreshTokenId(),
            updated
          )
        );
      },
      () -> {
        dataRepository.save(
          new SubscriptionsData(
            Generators.timeBasedEpochGenerator().generate().toString(),
            message.recipientId(),
            message.refreshTokenId(),
            created
          )
        );
      }
    );
  }

  @Serdeable
  public static record SubscribeKickEventsCommand(
    String recipientId,
    String refreshTokenId,
    List<String> events
  ) {}
}
