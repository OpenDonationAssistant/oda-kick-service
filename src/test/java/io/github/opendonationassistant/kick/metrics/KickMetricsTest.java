package io.github.opendonationassistant.kick.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

public class KickMetricsTest {

  @Test
  public void countsReceivedEventsPerType() {
    var registry = new SimpleMeterRegistry();
    var metrics = new KickMetrics(registry);

    metrics.eventReceived("channel.followed");
    metrics.eventReceived("channel.followed");
    metrics.eventReceived("kicks.gifted");

    assertEquals(
      2.0,
      count(registry, "channel.followed", KickMetrics.RECEIVED)
    );
    assertEquals(1.0, count(registry, "kicks.gifted", KickMetrics.RECEIVED));
  }

  @Test
  public void countsNoAccountEventsPerType() {
    var registry = new SimpleMeterRegistry();
    var metrics = new KickMetrics(registry);

    metrics.eventNoAccount("channel.followed");
    metrics.eventNoAccount("channel.followed");
    metrics.eventNoAccount("kicks.gifted");

    assertEquals(
      2.0,
      count(registry, "channel.followed", KickMetrics.NO_ACCOUNT)
    );
    assertEquals(1.0, count(registry, "kicks.gifted", KickMetrics.NO_ACCOUNT));
  }

  @Test
  public void countsInvalidEventsPerType() {
    var registry = new SimpleMeterRegistry();
    var metrics = new KickMetrics(registry);

    metrics.eventInvalid("channel.subscription.new");
    metrics.eventInvalid("channel.subscription.new");
    metrics.eventInvalid(null);

    assertEquals(
      2.0,
      count(registry, "channel.subscription.new", KickMetrics.INVALID)
    );
    assertEquals(
      1.0,
      count(registry, KickMetrics.UNKNOWN, KickMetrics.INVALID)
    );
  }

  @Test
  public void mergesAllOutcomesIntoSingleCounterName() {
    var registry = new SimpleMeterRegistry();
    var metrics = new KickMetrics(registry);

    metrics.eventReceived("channel.followed");
    metrics.eventInvalid("channel.followed");
    metrics.eventNoAccount("channel.followed");

    assertEquals(
      3,
      registry.getMeters().size(),
      "one counter name, one series per outcome"
    );
    registry
      .getMeters()
      .forEach(it ->
        assertEquals(KickMetrics.EVENTS_METRIC_NAME, it.getId().getName())
      );
  }

  @Test
  public void collapsesNullAndUnknownTypesIntoOneSeries() {
    var registry = new SimpleMeterRegistry();
    var metrics = new KickMetrics(registry);

    metrics.eventReceived(null);
    metrics.eventReceived("garbage");

    assertEquals(2.0, count(registry, KickMetrics.UNKNOWN, KickMetrics.RECEIVED));
    assertFalse(
      hasSeries(registry, "garbage"),
      "unknown header values must not create their own series"
    );
    assertEquals(1, registry.getMeters().size());
  }

  @Test
  public void keepsEverySubscribedTypeAsItsOwnSeries() {
    var registry = new SimpleMeterRegistry();
    var metrics = new KickMetrics(registry);

    KickMetrics.KNOWN_TYPES.forEach(metrics::eventReceived);

    assertEquals(KickMetrics.KNOWN_TYPES.size(), registry.getMeters().size());
    KickMetrics.KNOWN_TYPES.forEach(type ->
      assertTrue(
        hasSeries(registry, type),
        "known type %s must keep its own series".formatted(type)
      )
    );
  }

  private double count(
    SimpleMeterRegistry registry,
    String type,
    String outcome
  ) {
    var counter = registry
      .find(KickMetrics.EVENTS_METRIC_NAME)
      .tag(KickMetrics.TYPE_TAG, type)
      .tag(KickMetrics.OUTCOME_TAG, outcome)
      .counter();
    return counter == null ? 0.0 : counter.count();
  }

  private boolean hasSeries(SimpleMeterRegistry registry, String type) {
    var counter = registry
      .find(KickMetrics.EVENTS_METRIC_NAME)
      .tag(KickMetrics.TYPE_TAG, type)
      .counter();
    return counter != null;
  }
}
