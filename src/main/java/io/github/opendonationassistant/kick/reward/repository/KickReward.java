package io.github.opendonationassistant.kick.reward.repository;

import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.integration.KickClient;
import io.github.opendonationassistant.integration.KickDataClient.DataWrapper;
import io.github.opendonationassistant.integration.KickDataClient.FailedRedemption;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageRequest;
import io.github.opendonationassistant.integration.KickDataClient.SendChatMessageResponse;
import io.github.opendonationassistant.integration.media.MediaRPC;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public class KickReward {

  private static final String SYSTEM = "kick";
  private static final String CHAT_MESSAGE_TYPE = "user";
  private static final int MAX_REASON_LENGTH = 150;

  public enum RefundResult {
    REFUNDED,
    ALREADY_RESOLVED,
    FAILED
  }

  private final ODALogger log = new ODALogger(this);
  private final RewardData data;
  private final String recipientId;
  private final String kickId;
  private final MediaRPC media;
  private final KickClient kick;

  public KickReward(
    RewardData data,
    String recipientId,
    String kickId,
    MediaRPC media,
    KickClient kick
  ) {
    this.data = data;
    this.recipientId = recipientId;
    this.kickId = kickId;
    this.media = media;
    this.kick = kick;
  }

  public RewardData data() {
    return data;
  }

  public String recipientId() {
    return recipientId;
  }

  public CompletableFuture<Void> addMedia(
    @Nullable String redemptionId,
    String input,
    String requester,
    String rewardTitle
  ) {
    if (redemptionId == null || redemptionId.isBlank()) {
      log.error(
        "Can't add media: reward redemption has no id",
        Map.of(
          "recipientId",
          recipientId,
          "rewardId",
          data.id(),
          "requester",
          Objects.toString(requester, "")
        )
      );
      sendFailureMessage(
        rewardTitle,
        requester,
        null,
        RefundResult.FAILED
      );
      return CompletableFuture.completedFuture(null);
    }
    final CompletableFuture<MediaRPC.@Nullable AddMediaResponse> pending;
    try {
      pending = media.add(
        new MediaRPC.AddMediaRequest(
          redemptionId,
          input,
          requester,
          recipientId,
          SYSTEM
        )
      );
    } catch (RuntimeException e) {
      logMediaFailure(redemptionId, e);
      // Internal failure details are not exposed to the viewer.
      decline(redemptionId, rewardTitle, requester, null);
      return CompletableFuture.completedFuture(null);
    }
    return pending.handle((response, error) -> {
      if (error != null) {
        logMediaFailure(redemptionId, error);
        // Internal failure details are not exposed to the viewer.
        decline(redemptionId, rewardTitle, requester, null);
        return null;
      }
      var mediaId = response == null ? null : response.id();
      if (mediaId == null || mediaId.isBlank()) {
        var reason = Optional.ofNullable(response)
          .map(MediaRPC.AddMediaResponse::message)
          .filter(message -> !message.isBlank())
          .orElse(null);
        log.warn(
          "Media was not added from reward",
          Map.of(
            "redemptionId",
            redemptionId,
            "recipientId",
            recipientId,
            "rewardId",
            data.id(),
            "message",
            Objects.toString(reason, "empty response")
          )
        );
        decline(redemptionId, rewardTitle, requester, reason);
        return null;
      }
      log.info(
        "Added media from reward",
        Map.of(
          "mediaId",
          mediaId,
          "redemptionId",
          redemptionId,
          "recipientId",
          recipientId,
          "rewardId",
          data.id()
        )
      );
      return null;
    });
  }

  private void logMediaFailure(String redemptionId, Throwable error) {
    log.error(
      "Failed to add media from reward",
      Map.of(
        "redemptionId",
        redemptionId,
        "recipientId",
        recipientId,
        "rewardId",
        data.id(),
        "error",
        Objects.toString(error.getMessage(), "unknown error")
      )
    );
  }

  private void decline(
    String redemptionId,
    String rewardTitle,
    String requester,
    @Nullable String reason
  ) {
    CompletableFuture<RefundResult> refunded;
    try {
      refunded = kick
        .rejectRedemption(redemptionId)
        .thenApply(response -> refundResult(redemptionId, response));
    } catch (RuntimeException e) {
      log.error(
        "Failed to return reward points",
        Map.of(
          "recipientId",
          recipientId,
          "redemptionId",
          redemptionId,
          "error",
          Objects.toString(e.getMessage(), "unknown error")
        )
      );
      sendFailureMessage(
        rewardTitle,
        requester,
        reason,
        RefundResult.FAILED
      );
      return;
    }
    refunded
      .handle((result, error) -> {
        if (error != null) {
          log.error(
            "Failed to return reward points",
            Map.of(
              "recipientId",
              recipientId,
              "redemptionId",
              redemptionId,
              "error",
              Objects.toString(error.getMessage(), "unknown error")
            )
          );
          return RefundResult.FAILED;
        }
        return result == null ? RefundResult.FAILED : result;
      })
      .thenAccept(result ->
        sendFailureMessage(rewardTitle, requester, reason, result)
      );
  }

  private RefundResult refundResult(
    String redemptionId,
    DataWrapper<List<FailedRedemption>> response
  ) {
    var envelope = Optional.ofNullable(response).map(DataWrapper::data);
    if (envelope.isEmpty()) {
      // A 200 without the data envelope is not evidence that the points were
      // returned: report the refund as failed instead of claiming success.
      log.error(
        "Kick reject response had no data",
        Map.of("recipientId", recipientId, "redemptionId", redemptionId)
      );
      return RefundResult.FAILED;
    }
    var failures = envelope.get();
    if (failures.isEmpty()) {
      log.info(
        "Returned reward points",
        Map.of("recipientId", recipientId, "redemptionId", redemptionId)
      );
      return RefundResult.REFUNDED;
    }
    var reasons = failures
      .stream()
      .map(it -> Objects.toString(it.reason(), "unknown"))
      .toList();
    if (reasons.stream().anyMatch("NOT_PENDING"::equalsIgnoreCase)) {
      // The streamer accepted or rejected the redemption first: the points are
      // already settled and there is nothing to return.
      log.info(
        "Redemption was already resolved, skipping the decline",
        Map.of("recipientId", recipientId, "redemptionId", redemptionId)
      );
      return RefundResult.ALREADY_RESOLVED;
    }
    log.error(
      "Kick did not reject the reward redemption",
      Map.of(
        "recipientId",
        recipientId,
        "redemptionId",
        redemptionId,
        "reasons",
        reasons.toString()
      )
    );
    return RefundResult.FAILED;
  }

  private void sendFailureMessage(
    String rewardTitle,
    String requester,
    @Nullable String reason,
    RefundResult result
  ) {
    var content = failureMessage(rewardTitle, requester, reason, result);
    try {
      kick
        .sendChatMessage(
          new SendChatMessageRequest(content, CHAT_MESSAGE_TYPE, kickId)
        )
        .handle((response, error) -> {
          if (error != null) {
            log.error(
              "Failed to report reward failure in chat",
              Map.of(
                "recipientId",
                recipientId,
                "rewardId",
                data.id(),
                "error",
                Objects.toString(error.getMessage(), "unknown error")
              )
            );
            return null;
          }
          var sent = Optional.ofNullable(response)
            .map(DataWrapper::data)
            .map(SendChatMessageResponse::isSent)
            .orElse(null);
          if (!Boolean.TRUE.equals(sent)) {
            log.warn(
              "Kick did not send the reward failure message",
              Map.of("recipientId", recipientId, "rewardId", data.id())
            );
          }
          return null;
        });
    } catch (RuntimeException e) {
      log.error(
        "Failed to report reward failure in chat",
        Map.of(
          "recipientId",
          recipientId,
          "rewardId",
          data.id(),
          "error",
          Objects.toString(e.getMessage(), "unknown error")
        )
      );
    }
  }

  private static String failureMessage(
    String rewardTitle,
    String requester,
    @Nullable String reason,
    RefundResult result
  ) {
    var message = new StringBuilder()
      .append('@')
      .append(requester)
      .append(", не удалось добавить награду «")
      .append(rewardTitle)
      .append("»");
    if (reason != null && !reason.isBlank()) {
      message.append(": ").append(truncate(reason, MAX_REASON_LENGTH));
    }
    message.append(
      switch (result) {
        case REFUNDED -> ". Баллы за награду возвращены.";
        case ALREADY_RESOLVED -> ". Награда уже была обработана.";
        case FAILED -> ". Баллы вернуть автоматически не удалось, обратитесь к стримеру.";
      }
    );
    return message.toString();
  }

  private static String truncate(String value, int maxLength) {
    return value.length() <= maxLength
      ? value
      : value.substring(0, maxLength) + "…";
  }
}
