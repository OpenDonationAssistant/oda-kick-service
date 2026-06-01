package io.github.opendonationassistant.kick.listener.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.uuid.Generators;
import io.github.opendonationassistant.kick.account.KickAccountDataRepository;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.io.IOException;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@MicronautTest(environments = "allinone", transactional = false)
@ExtendWith(InstancioExtension.class)
public class LinkAccountHandlerTest {

  @Inject
  KickAccountDataRepository repository;

  @Inject
  KickAccountRepository accountRepository;

  @Inject
  ObjectMapper mapper;

  String refreshTokenId = Generators.timeBasedEpochGenerator()
    .generate()
    .toString();

  @Test
  public void testCreatingAccount() throws IOException {
    var handler = new LinkAccountHandler(mapper, accountRepository);
    handler.handle(
      new LinkAccountHandler.LinkKickAccount(
        "testkick",
        "testuser",
        "testrecipient",
        refreshTokenId
      )
    );
    var expected = repository.findOneByRecipientIdAndRefreshTokenId(
      "testrecipient",
      refreshTokenId
    );
    assertTrue(expected.isPresent());
    assertNotNull(expected.get().id());
    assertEquals("testkick", expected.get().kickId());
    assertEquals("testuser", expected.get().username());
    assertEquals("testrecipient", expected.get().recipientId());
    assertEquals(refreshTokenId, expected.get().refreshTokenId());
  }

  @Test
  public void testDontCreatingDublicate() throws IOException {
    repository.save(
      new io.github.opendonationassistant.kick.account.KickAccountData(
        Generators.timeBasedEpochGenerator().generate().toString(),
        "testkick",
        "testuser",
        "testrecipient",
        refreshTokenId
      )
    );
    var spy = spy(accountRepository);
    var handler = new LinkAccountHandler(mapper, spy);
    handler.handle(
      new LinkAccountHandler.LinkKickAccount(
        "testkick",
        "testuser",
        "testrecipient",
        refreshTokenId
      )
    );
    verify(spy, never()).create(any());
  }
}
