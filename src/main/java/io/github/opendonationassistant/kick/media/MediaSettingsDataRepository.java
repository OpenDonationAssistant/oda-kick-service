package io.github.opendonationassistant.kick.media;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface MediaSettingsDataRepository
  extends CrudRepository<MediaSettings, String> {}
