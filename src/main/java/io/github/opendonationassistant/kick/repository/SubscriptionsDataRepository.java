package io.github.opendonationassistant.kick.repository;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface SubscriptionsDataRepository
  extends CrudRepository<SubscriptionsData, String> {
  List<SubscriptionsData> findByRecipientIdAndTokenId(
    String name,
    String token
  );
}
