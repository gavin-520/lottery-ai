package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.dto.NotificationLogItem;
import com.lottery.entity.NotificationLog;
import com.lottery.mapper.NotificationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

@Service
public class WebhookNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationService.class);

    private final NotificationLogMapper notificationLogMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${lottery.webhook.enabled:false}")
    private boolean enabled;

    @Value("${lottery.webhook.url:}")
    private String webhookUrl;

    @Value("${lottery.webhook.secret:}")
    private String webhookSecret;

    @Value("${lottery.webhook.timeout-ms:3000}")
    private int timeoutMs;

    public WebhookNotificationService(NotificationLogMapper notificationLogMapper,
                                      ObjectMapper objectMapper) {
        this.notificationLogMapper = notificationLogMapper;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public void notifyBreach(Map<String, Object> payload, String correlationId, Long breachId) {
        deliver("sla.breach", payload, correlationId, breachId, null);
    }

    public void notifySyncFailed(Map<String, Object> payload, String correlationId, Long syncLogId) {
        deliver("sync.failed", payload, correlationId, null, syncLogId);
    }

    private void deliver(String eventType, Map<String, Object> payload,
                         String correlationId, Long breachId, Long syncLogId) {
        NotificationLog entry = new NotificationLog();
        entry.setChannel("webhook");
        entry.setEventType(eventType);
        entry.setCorrelationId(correlationId);
        entry.setBreachId(breachId);
        entry.setSyncLogId(syncLogId);

        if (!enabled || !StringUtils.hasText(webhookUrl)) {
            entry.setStatus("SKIPPED");
            entry.setTargetUrl(webhookUrl);
            try {
                entry.setPayload(objectMapper.writeValueAsString(payload));
            } catch (Exception ignored) {
                entry.setPayload("{}");
            }
            notificationLogMapper.insert(entry);
            return;
        }

        entry.setTargetUrl(webhookUrl);
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "eventType", eventType,
                    "correlationId", correlationId,
                    "payload", payload
            ));
            entry.setPayload(body);

            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            if (StringUtils.hasText(webhookSecret)) {
                spec = spec.header("X-Webhook-Signature", sign(body));
            }
            var response = spec.retrieve().toBodilessEntity();
            entry.setStatus("SUCCESS");
            entry.setHttpStatus(response.getStatusCode().value());
        } catch (Exception ex) {
            entry.setStatus("FAILED");
            entry.setErrorMessage(ex.getMessage());
            log.warn("Webhook delivery failed: {}", ex.getMessage());
        }
        notificationLogMapper.insert(entry);
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return "";
        }
    }

    public Page<NotificationLogItem> list(int page, int size) {
        Page<NotificationLog> result = notificationLogMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<NotificationLog>().orderByDesc(NotificationLog::getId)
        );
        Page<NotificationLogItem> mapped = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapped.setRecords(result.getRecords().stream()
                .map(n -> new NotificationLogItem(
                        n.getId(), n.getChannel(), n.getEventType(), n.getTargetUrl(),
                        n.getStatus(), n.getHttpStatus(), n.getErrorMessage(),
                        n.getCorrelationId(), n.getBreachId(), n.getSyncLogId(), n.getCreatedAt()))
                .toList());
        return mapped;
    }
}
