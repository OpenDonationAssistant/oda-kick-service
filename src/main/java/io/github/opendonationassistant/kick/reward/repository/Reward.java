package io.github.opendonationassistant.kick.reward.repository;

import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.rabbit.RabbitClient;

public class Reward {

  private final RewardData data;
  private final RabbitClient rabbit;
  private final KickClient kick;

  public Reward(RewardData data, RabbitClient rabbit, KickClient kick) {
    this.data = data;
    this.rabbit = rabbit;
    this.kick = kick;
  }

  public RewardData data() {
    return data;
  }
}
