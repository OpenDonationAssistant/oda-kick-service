package io.github.opendonationassistant;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.events.AbstractMessageHandler;
import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient;
import io.github.opendonationassistant.integration.KickDataClient.Created;
import io.github.opendonationassistant.kick.account.KickAccountRepository;
import io.github.opendonationassistant.kick.reward.repository.RewardRepository;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Singleton
public class WidgetChangedEventListener
  extends AbstractMessageHandler<
    WidgetChangedEventListener.WidgetChangedEvent
  > {

  private ODALogger log = new ODALogger(this);
  private static final String WIDGET_TYPE = "media";

  private final TimeBasedEpochGenerator uuid =
    Generators.timeBasedEpochGenerator();
  private final RewardRepository rewardRepository;
  private final KickAccountRepository accountRepository;
  private final KickClient kick;

  @Inject
  public WidgetChangedEventListener(
    ObjectMapper mapper,
    RewardRepository rewardRepository,
    KickAccountRepository accountRepository,
    KickClient kick
  ) {
    super(mapper);
    this.rewardRepository = rewardRepository;
    this.accountRepository = accountRepository;
    this.kick = kick;
  }

  @Override
  public void handle(WidgetChangedEvent event) throws IOException {
    if (!"updated".equals(event.type())) {
      return;
    }

    var widget = event.widget();
    if (widget == null) {
      return;
    }
    if (!WIDGET_TYPE.equals(widget.type())) {
      return;
    }

    var config = widget.config();
    if (config == null) {
      return;
    }

    var properties = config.properties();
    if (properties == null) {
      return;
    }

    var ownerId = widget.ownerId();
    if (ownerId == null) {
      return;
    }
    var widgetId = widget.id();
    accountRepository
      .findByRecipientId(ownerId)
      .thenAccept(account -> {
        account.ifPresent(it -> {
          processSystem(
            widgetId,
            properties,
            "kick",
            ownerId,
            it.data().refreshTokenId()
          );
        });
      });
    // if (account.isEmpty()) {
    //   return;
    // }

    // var refreshTokenId = account
    // var recipientId = ownerId;

    // rewardRepository.deleteByRecipientId(ownerId);
    // processSystem(properties, "kick", recipientId, refreshTokenId);
  }

  private void processSystem(
    String widgetId,
    List<WidgetProperty> properties,
    String system,
    String recipientId,
    String refreshTokenId
  ) {
    var enabled = findBoolProperty(
      properties,
      system + "PointsRequestsEnabled"
    );
    log.info("music-" + system + "-request-title: " + enabled);
    if (!enabled) {
      return;
    }

    var title = findStringProperty(
      properties,
      "music-" + system + "-request-title"
    );
    log.info("music-" + system + "-request-title: " + title);
    if (title == null) {
      return;
    }
    Integer cost = findIntProperty(
      properties,
      "music-" + system + "-request-cost"
    );
    log.info("music-" + system + "-request-cost: " + cost);
    if (cost == null) {
      return;
    }
    rewardRepository.create(title, widgetId, cost, recipientId, refreshTokenId).join();
  }

  private boolean findBoolProperty(
    List<WidgetProperty> properties,
    String name
  ) {
    return properties
      .stream()
      .filter(p -> name.equals(p.name()))
      .findFirst()
      .map(WidgetProperty::value)
      .map(v -> Boolean.TRUE.equals(v))
      .orElse(false);
  }

  private @Nullable String findStringProperty(
    List<WidgetProperty> properties,
    String name
  ) {
    return properties
      .stream()
      .filter(p -> name.equals(p.name()))
      .findFirst()
      .map(WidgetProperty::value)
      .map(Object::toString)
      .orElse(null);
  }

  private @Nullable Integer findIntProperty(
    List<WidgetProperty> properties,
    String name
  ) {
    return properties
      .stream()
      .filter(p -> name.equals(p.name()))
      .findFirst()
      .map(WidgetProperty::value)
      .filter(v -> v instanceof Number)
      .map(v -> ((Number) v).intValue())
      .orElse(null);
  }

  @Serdeable
  public static record WidgetChangedEvent(
    String type,
    Widget widget,
    String source,
    @Nullable String originId
  ) {}

  @Serdeable
  public static record Widget(
    String id,
    String type,
    Integer sortOrder,
    String name,
    Boolean enabled,
    String ownerId,
    WidgetConfig config
  ) {}

  @Serdeable
  public static record WidgetConfig(List<WidgetProperty> properties) {}

  @Serdeable
  public static record WidgetProperty(
    String name,
    @Nullable String displayName,
    @Nullable String type,
    @Nullable Object value
  ) {}
}
