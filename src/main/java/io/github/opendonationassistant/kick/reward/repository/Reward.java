package io.github.opendonationassistant.kick.reward.repository;

import io.micronaut.serde.annotation.Serdeable;

public class Reward {

  private final RewardData data;

  public Reward(RewardData data) {
    this.data = data;
  }

  public RewardData data() {
    return data;
  }

  // public void sendAddMediaCommand(String url, String requester) {
  //   rabbit.sendCommand(
  //     new AddMediaCommand(url, requester, data.recipientId(), "kick")
  //   );
  // }

  @Serdeable
  public static record AddMediaCommand(
    String url,
    String requester,
    String recipientId,
    String system
  ) {}
}
