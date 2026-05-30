package io.github.opendonationassistant;

import io.github.opendonationassistant.events.MessageProcessor;
import io.github.opendonationassistant.rabbit.Exchange;
import io.micronaut.messaging.annotation.MessageHeader;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import io.micronaut.rabbitmq.bind.RabbitAcknowledgement;
import jakarta.inject.Inject;
import java.util.Map;

@RabbitListener(executor = "command-listener")
public class CommandListener {

  public static final String QUEUE_NAME = "kick.commands";
  public static final io.github.opendonationassistant.rabbit.Queue QUEUE =
    new io.github.opendonationassistant.rabbit.Queue(QUEUE_NAME);

  public static final Exchange BINDING = Exchange.Exchange(
    "commands",
    Map.of(
      "command.SubscribeKickEventsCommand",
      QUEUE,
      "command.SubscribeAllKickEventsCommand",
      QUEUE,
      "command.UnsubscribeKickEventsCommand",
      QUEUE,
      "command.LinkKickAccount",
      QUEUE
    )
  );

  private MessageProcessor processor;

  @Inject
  public CommandListener(MessageProcessor processor) {
    this.processor = processor;
  }

  @Queue(QUEUE_NAME)
  public void listenHistoryCommands(
    @MessageHeader("type") String type,
    byte[] payload,
    RabbitAcknowledgement ack
  ) {
    processor.process(type, payload, ack);
  }
}
