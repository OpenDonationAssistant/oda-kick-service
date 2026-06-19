package io.github.opendonationassistant.kick.reward.repository;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface RewardDataRepository
  extends CrudRepository<RewardData, String> {
  List<RewardData> findByWidgetId(String widgetId);
}
