package io.github.opendonationassistant;

import io.github.opendonationassistant.rabbit.AMQPConfiguration;
import io.github.opendonationassistant.rabbit.RabbitClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.rabbitmq.connect.ChannelInitializer;
import io.micronaut.rabbitmq.connect.ChannelPool;
import io.micronaut.runtime.Micronaut;
import io.micronaut.serde.ObjectMapper;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.List;

@OpenAPIDefinition(info = @Info(title = "oda-kick-service"))
@Factory
public class Application {

  public static void main(String[] args) {
    Micronaut.run(Application.class, args);
  }

  @Singleton
  public ChannelInitializer rabbitConfiguration() {
    return new AMQPConfiguration(
      List.of(CommandListener.BINDING, WidgetChangedEventListener.BINDING)
    );
  }

  @Singleton
  @Named("commands")
  public RabbitClient commandsFacade(ChannelPool pool, ObjectMapper mapper) {
    return new RabbitClient(pool, mapper, "commands");
  }

  @Singleton
  @Named("events")
  public RabbitClient kickFacade(ChannelPool pool, ObjectMapper mapper) {
    return new RabbitClient(pool, mapper, "kick");
  }
}
