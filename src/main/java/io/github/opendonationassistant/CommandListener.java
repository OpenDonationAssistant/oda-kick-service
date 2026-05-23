package io.github.opendonationassistant;

import io.github.opendonationassistant.events.MessageProcessor;
import io.micronaut.messaging.annotation.MessageHeader;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import io.micronaut.rabbitmq.bind.RabbitAcknowledgement;
import jakarta.inject.Inject;

@RabbitListener(executor = "command-listener")
public class CommandListener {

  private MessageProcessor processor;

  @Inject
  public CommandListener(MessageProcessor processor) {
    this.processor = processor;
  }

  @Queue("kick.commands")
  public void listenHistoryCommands(
    @MessageHeader("type") String type,
    byte[] payload,
    RabbitAcknowledgement ack
  ) {
    processor.process(type, payload, ack);
  }
}
