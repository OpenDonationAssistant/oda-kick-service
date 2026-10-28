package io.github.opendonationassistant.kick.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Set;
import org.jspecify.annotations.Nullable;

@Singleton
public class KickMetrics {

  public static final String EVENTS_METRIC_NAME = "kick.events";
  public static final String TYPE_TAG = "type";
  public static final String OUTCOME_TAG = "outcome";
  public static final String RECEIVED = "received";
  public static final String INVALID = "invalid";
  public static final String NO_ACCOUNT = "no-account";
  public static final String UNKNOWN = "unknown";

  public static final Set<String> KNOWN_TYPES = Set.of(
    "channel.followed",
    "channel.subscription.new",
    "channel.subscription.renewal",
    "channel.subscription.gifts",
    "channel.reward.redemption.updated",
    "livestream.status.updated",
    "livestream.metadata.updated",
    "kicks.gifted"
  );

  private final MeterRegistry registry;

  @Inject
  public KickMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void eventReceived(@Nullable String type) {
    increment(RECEIVED, type);
  }

  public void eventNoAccount(@Nullable String type) {
    increment(NO_ACCOUNT, type);
  }

  public void eventInvalid(@Nullable String type) {
    increment(INVALID, type);
  }

  private void increment(String outcome, @Nullable String type) {
    registry
      .counter(
        EVENTS_METRIC_NAME,
        TYPE_TAG,
        normalise(type),
        OUTCOME_TAG,
        outcome
      )
      .increment();
  }

  private String normalise(@Nullable String type) {
    return type != null && KNOWN_TYPES.contains(type) ? type : UNKNOWN;
  }
}
