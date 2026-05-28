package io.github.opendonationassistant.kick.listener.handlers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient;
import io.github.opendonationassistant.kick.listener.handlers.UnsubscribeEventsHandler.UnsubscribeKickEventsCommand;
import io.github.opendonationassistant.kick.subscription.SubscriptionsData;
import io.github.opendonationassistant.kick.subscription.SubscriptionsDataRepository;
import io.micronaut.serde.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class UnsubscribeEventsHandlerTest {

  ObjectMapper mapper = ObjectMapper.getDefault();
  KickClient kick = mock(KickClient.class);
  SubscriptionsDataRepository repository = mock(
    SubscriptionsDataRepository.class
  );
  UnsubscribeEventsHandler handler = new UnsubscribeEventsHandler(
    mapper,
    kick,
    repository
  );

  @Test
  public void testDeletingAllEvents() throws IOException {
    when(kick.unsubscribe(anyString(), anyString(), anyList())).thenReturn(
      CompletableFuture.completedFuture(null)
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
      new UnsubscribeKickEventsCommand("testuser", "tokenId", null)
    );
    verify(kick).unsubscribe(
      "testuser",
      "tokenId",
      List.of("oldid1", "oldid2")
    );
    verify(repository).deleteById("id");
  }

  @Test
  public void testDeletingSomeEvents() throws IOException {
    when(kick.unsubscribe(anyString(), anyString(), anyList())).thenReturn(
      CompletableFuture.completedFuture(null)
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
      new UnsubscribeKickEventsCommand(
        "testuser",
        "tokenId",
        List.of("oldname1")
      )
    );
    verify(kick).unsubscribe("testuser", "tokenId", List.of("oldid1"));
    verify(repository).update(
      argThat(it -> {
        return (
          it.id().equals("id") &&
          it.tokenId().equals("tokenId") &&
          it.events().size() == 1 &&
          it.events().get(0).id().equals("oldid2") &&
          it.events().get(0).name().equals("oldname2") &&
          it.events().get(0).version() == 1
        );
      })
    );
  }
}
