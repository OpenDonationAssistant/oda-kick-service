package io.github.opendonationassistant.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageRequest;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;

/**
 * Exercises the declarative {@link KickDataClient} over real HTTP against
 * MockServer: the point is the actual wire contract (method, path, bearer
 * header and JSON body) that a serde round-trip cannot see.
 *
 * <p>MockServer is started by {@link MockServerExtension} (the Jetty/Netty server
 * lifecycle and the port allocation are the extension's job); the client is
 * taken from a minimal Micronaut context that only scans this package, so no
 * database, RabbitMQ or Flyway beans are involved.
 */
@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = { KickDataClientMockServerTest.MOCK_SERVER_PORT })
public class KickDataClientMockServerTest {

  static final int MOCK_SERVER_PORT = 8080;

  private static final String CHAT_PATH = "/public/v1/chat";
  private static final String TOKEN = "Bearer token-1";

  private static final ObjectMapper mapper = ObjectMapper.getDefault();

  private static ApplicationContext context;
  private static KickDataClient client;

  private final ClientAndServer mockServer;

  public KickDataClientMockServerTest(ClientAndServer mockServer) {
    this.mockServer = mockServer;
  }

  @BeforeAll
  static void startClient() {
    context = ApplicationContext.builder(
      Map.of(
        "micronaut.http.services.kick-data.url",
        "http://localhost:" + MOCK_SERVER_PORT
      )
    )
      .packages("io.github.opendonationassistant.integration")
      .start();
    client = context.getBean(KickDataClient.class);
  }

  @AfterAll
  static void stopClient() {
    if (context != null) {
      context.stop();
    }
  }

  @AfterEach
  void resetMockServer() {
    mockServer.reset();
  }

  @Test
  public void sendsChatMessageAsJsonNumberFreeBody() throws IOException {
    mockServer
      .when(request().withMethod("POST").withPath(CHAT_PATH))
      .respond(
        response()
          .withStatusCode(200)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {"data":{"is_sent":true,"message_id":"828f83bb-e391-4c78-9e91-af35a172840e"}}
            """
          )
      );

    var result = client
      .sendChatMessage(
        TOKEN,
        new SendChatMessageRequest("hello", "user", "123")
      )
      .join();

    var recorded = mockServer.retrieveRecordedRequests(
      request().withMethod("POST").withPath(CHAT_PATH)
    );
    assertEquals(1, recorded.length);
    assertEquals(TOKEN, recorded[0].getFirstHeader("Authorization"));

    var body = Objects.requireNonNull(
      mapper.readValue(
        recorded[0].getBodyAsString(),
        Argument.mapOf(String.class, Object.class)
      )
    );
    assertEquals("hello", body.get("content"));
    assertEquals("user", body.get("type"));
    // the account's Kick id is an opaque string: it must stay a JSON string
    assertInstanceOf(String.class, body.get("broadcaster_user_id"));
    assertEquals("123", body.get("broadcaster_user_id"));
    assertFalse(
      recorded[0].getBodyAsString().contains("\"broadcaster_user_id\":123"),
      recorded[0].getBodyAsString()
    );
    assertEquals(3, body.size(), recorded[0].getBodyAsString());

    var data = Objects.requireNonNull(result.data());
    assertEquals(Boolean.TRUE, data.isSent());
    assertEquals("828f83bb-e391-4c78-9e91-af35a172840e", data.messageId());
  }

  @Test
  public void keepsAbsentIsSentDistinguishableFromFalse() {
    mockServer
      .when(request().withMethod("POST").withPath(CHAT_PATH))
      .respond(
        response()
          .withStatusCode(200)
          .withHeader("Content-Type", "application/json")
          .withBody("{\"data\":{}}")
      );

    var data = Objects.requireNonNull(
      client
        .sendChatMessage(TOKEN, new SendChatMessageRequest("hello", "user", "123"))
        .join()
        .data()
    );

    assertNull(data.isSent());
    assertNull(data.messageId());
  }

  @Test
  public void toleratesNullDataEnvelope() {
    mockServer
      .when(request().withMethod("POST").withPath(CHAT_PATH))
      .respond(
        response()
          .withStatusCode(200)
          .withHeader("Content-Type", "application/json")
          .withBody("{\"data\":null}")
      );

    assertNull(
      client
        .sendChatMessage(TOKEN, new SendChatMessageRequest("hello", "user", "123"))
        .join()
        .data()
    );
  }

  @Test
  public void completesExceptionallyWhenKickRejectsTheToken() {
    mockServer
      .when(request().withMethod("POST").withPath(CHAT_PATH))
      .respond(
        response()
          .withStatusCode(403)
          .withHeader("Content-Type", "application/json")
          .withBody("{\"data\":null,\"message\":\"Forbidden\"}")
      );

    // The caller (SendKickMessageHandler) relies on this: a failing future
    // propagates out of handle() so the command stays unacknowledged.
    assertThrows(CompletionException.class, () ->
      client
        .sendChatMessage(TOKEN, new SendChatMessageRequest("hello", "user", "123"))
        .join()
    );
  }

  @Test
  public void sendsChatMessageWithGivenContentVerbatim() throws IOException {
    mockServer
      .when(request().withMethod("POST").withPath(CHAT_PATH))
      .respond(
        response()
          .withStatusCode(200)
          .withHeader("Content-Type", "application/json")
          .withBody("{\"data\":{\"is_sent\":true}}")
      );

    var content = "  donation: \"5$\" \n second line ";
    client
      .sendChatMessage(TOKEN, new SendChatMessageRequest(content, "user", "987654"))
      .join();

    var recorded = mockServer.retrieveRecordedRequests(
      request().withMethod("POST").withPath(CHAT_PATH)
    );
    var body = Objects.requireNonNull(
      mapper.readValue(
        recorded[0].getBodyAsString(),
        Argument.mapOf(String.class, Object.class)
      )
    );
    assertEquals(content, body.get("content"));
    assertInstanceOf(String.class, body.get("broadcaster_user_id"));
    assertTrue(recorded[0].getBodyAsString().contains("\\n"));
  }
}
