package io.github.opendonationassistant.kick.reward.repository;

import io.github.opendonationassistant.integration.KickDataClient;
import io.github.opendonationassistant.rabbit.RabbitClient;
import io.micronaut.serde.annotation.Serdeable;

public class Reward {

  private final RewardData data;
  private final RabbitClient rabbit;
  private final KickDataClient kick;

  public Reward(RewardData data, RabbitClient rabbit, KickDataClient kick) {
    this.data = data;
    this.rabbit = rabbit;
    this.kick = kick;
  }

  public RewardData data() {
    return data;
  }

  public void sendAddMediaCommand(String url, String requester) {
    rabbit.sendCommand(
      new AddMediaCommand(url, requester, data.recipientId(), "kick")
    );
  }

  @Serdeable
  public static record AddMediaCommand(
    String url,
    String requester,
    String recipientId,
    String system
  ) {}
}
