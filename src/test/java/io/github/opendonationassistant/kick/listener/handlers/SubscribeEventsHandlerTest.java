package io.github.opendonationassistant.kick.listener.handlers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.integration.KickDataClient;
import io.github.opendonationassistant.integration.KickDataClient.CreatedSubscription;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.EventSubscription;
import io.github.opendonationassistant.integration.KickDataClient.SubscriptionRequest;
import io.github.opendonationassistant.kick.listener.handlers.SubscribeEventsHandler.SubscribeKickEventsCommand;
import io.github.opendonationassistant.kick.repository.SubscriptionsData;
import io.github.opendonationassistant.kick.repository.SubscriptionsDataRepository;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SubscribeEventsHandlerTest {

  KickDataClient kick = Mockito.mock(KickDataClient.class);
  ObjectMapper mapper = ObjectMapper.getDefault();
  SubscriptionsDataRepository repository = Mockito.mock(
    SubscriptionsDataRepository.class
  );

  SubscribeEventsHandler handler = new SubscribeEventsHandler(
    mapper,
    kick,
    repository
  );

  @Test
  public void testCreatingNewSubscriptions() throws IOException {
    when(
      kick.subscribe(anyString(), any(SubscriptionRequest.class))
    ).thenReturn(
      CompletableFuture.completedFuture(
        new DataWrapper<>(
          List.of(
            new CreatedSubscription("id", "name", 1),
            new CreatedSubscription("id2", "name2", 1)
          )
        )
      )
    );
    handler.handle(
      new SubscribeKickEventsCommand(
        "testuser",
        "token",
        "tokenId",
        List.of("name", "name2")
      )
    );
    Mockito.verify(kick).subscribe(
      "token",
      new SubscriptionRequest(
        List.of(
          new EventSubscription("name", 1),
          new EventSubscription("name2", 1)
        ),
        "webhook"
      )
    );
    Mockito.verify(repository).save(
      argThat(
        arg ->
          arg.id() != null &&
          "tokenId".equals(arg.tokenId()) &&
          arg.events().size() == 2 &&
          arg
            .events()
            .contains(new SubscriptionsData.Subscription("id", "name", 1)) &&
          arg
            .events()
            .contains(new SubscriptionsData.Subscription("id2", "name2", 1))
      )
    );
  }

  @Test
  public void testAddingSubscriptionsToExisting() throws IOException {
    when(
      kick.subscribe(anyString(), any(SubscriptionRequest.class))
    ).thenReturn(
      CompletableFuture.completedFuture(
        new DataWrapper<>(
          List.of(
            new CreatedSubscription("id", "name", 1),
            new CreatedSubscription("id2", "name2", 1)
          )
        )
      )
    );
    when(
      repository.findByRecipientIdAndTokenId(anyString(), anyString())
    ).thenReturn(
      List.of(
        new SubscriptionsData(
          "id",
          "testuser",
          "tokenId",
          List.of(
            new SubscriptionsData.Subscription("oldid1", "oldname1", 1),
            new SubscriptionsData.Subscription("oldid2", "oldname2", 1)
          )
        )
      )
    );
    handler.handle(
      new SubscribeKickEventsCommand(
        "testuser",
        "token",
        "tokenId",
        List.of("name", "name2")
      )
    );
    Mockito.verify(repository).update(
      argThat(
        arg ->
          "id".equals(arg.id()) &&
          "tokenId".equals(arg.tokenId()) &&
          arg.events().size() == 4 &&
          arg
            .events()
            .contains(
              new SubscriptionsData.Subscription("oldid1", "oldname1", 1)
            ) &&
          arg
            .events()
            .contains(
              new SubscriptionsData.Subscription("oldid2", "oldname2", 1)
            ) &&
          arg
            .events()
            .contains(new SubscriptionsData.Subscription("id", "name", 1)) &&
          arg
            .events()
            .contains(new SubscriptionsData.Subscription("id2", "name2", 1))
      )
    );
  }
}
