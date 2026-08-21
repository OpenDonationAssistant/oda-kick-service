package io.github.opendonationassistant.integration;

import io.github.opendonationassistant.kick.command.GetChannelInfo.ChannelInfo;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import java.util.concurrent.CompletableFuture;

@Client("kick-web")
public interface KickWebClient {
  @Get("/api/v2/channels/{slug}")
  CompletableFuture<ChannelInfo> getChannelInfo(String slug);
}
