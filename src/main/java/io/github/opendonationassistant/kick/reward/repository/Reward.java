package io.github.opendonationassistant.kick.reward.repository;

import io.github.opendonationassistant.rabbit.RabbitClient;
import io.micronaut.serde.annotation.Serdeable;

public class Reward {

  private final RewardData data;

  public Reward(RewardData data) {
    this.data = data;
  }

  public RewardData data() {
    return data;
  }

  // public void handleInvocation() {
  //   rabbit.sendCommand(
  //     new AddMediaCommand(url, requester, data.recipientId(), "kick")
  //   );
  // }
}
