package io.github.opendonationassistant.kick.reward.repository;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class RewardRepository {

  private final RewardDataRepository repository;
  
  @Inject
  public RewardRepository(RewardDataRepository repository) {
    this.repository = repository;
  }

  public Optional<Reward> findById(String id) {
    return repository.findById(id)
      .map(this::convert);
  }

  private Reward convert(RewardData data){
    return new Reward(data);
  }

}
