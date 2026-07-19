package com.lottery.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.entity.LotteryHistory;
import com.lottery.service.ExternalApiSlaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-style external lottery API adapter with retry, timeout and SLA logging.
 */
@Component
@ConditionalOnProperty(name = "lottery.sync.feed-type", havingValue = "external")
public class ExternalApiFeedFetcher implements LotteryFeedFetcher {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiFeedFetcher.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ExternalApiSlaService slaService;
    private final String feedUrl;
    private final String apiKeyHeader;
    private final String apiKey;
    private final int maxRetries;
    private final int timeoutMs;

    public ExternalApiFeedFetcher(
            @Value("${lottery.sync.external.url:}") String url,
            @Value("${lottery.sync.external.api-key:}") String apiKey,
            @Value("${lottery.sync.external.api-key-header:X-API-Key}") String apiKeyHeader,
            @Value("${lottery.sync.external.max-retries:3}") int maxRetries,
            @Value("${lottery.sync.external.timeout-ms:5000}") int timeoutMs,
            ObjectMapper objectMapper,
            ExternalApiSlaService slaService) {
        this.feedUrl = url;
        this.apiKey = apiKey;
        this.apiKeyHeader = apiKeyHeader;
        this.maxRetries = Math.max(1, maxRetries);
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
        this.slaService = slaService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String source() {
        return "external-api";
    }

    @Override
    public List<LotteryHistory> fetch() {
        if (feedUrl == null || feedUrl.isBlank()) {
            throw new FeedFetchException("External feed URL is not configured", "CONFIG_ERROR");
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long started = System.currentTimeMillis();
            try {
                var request = restClient.get().uri(feedUrl);
                if (apiKey != null && !apiKey.isBlank()) {
                    request = request.header(apiKeyHeader, apiKey);
                }
                request = request.header(HttpHeaders.ACCEPT, "application/json");
                String body = request.retrieve().body(String.class);
                int latency = (int) (System.currentTimeMillis() - started);
                slaService.logCall(source(), feedUrl, latency, 200, true, null);

                JsonNode root = objectMapper.readTree(body);
                JsonNode items = root.isArray() ? root : root.path("data");
                if (items.isMissingNode() || !items.isArray()) {
                    items = root.path("result");
                }

                List<LotteryHistory> records = new ArrayList<>();
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        records.add(parseRecord(item));
                    }
                }
                return records;
            } catch (HttpStatusCodeException ex) {
                int latency = (int) (System.currentTimeMillis() - started);
                String errorType = ex.getStatusCode().value() == 429 ? "RATE_LIMIT" : "HTTP_ERROR";
                slaService.logCall(source(), feedUrl, latency, ex.getStatusCode().value(), false, errorType);
                lastError = ex;
                log.warn("External API attempt {}/{} failed: HTTP {}", attempt, maxRetries, ex.getStatusCode().value());
            } catch (Exception ex) {
                int latency = (int) (System.currentTimeMillis() - started);
                slaService.logCall(source(), feedUrl, latency, null, false, "NETWORK_ERROR");
                lastError = ex;
                log.warn("External API attempt {}/{} failed: {}", attempt, maxRetries, ex.getMessage());
            }
            sleepBackoff(attempt);
        }

        String message = lastError != null ? lastError.getMessage() : "Unknown external API failure";
        if (lastError instanceof HttpStatusCodeException httpEx) {
            throw new FeedFetchException(message, "HTTP_ERROR", httpEx.getStatusCode().value());
        }
        throw new FeedFetchException(message, "NETWORK_ERROR");
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private LotteryHistory parseRecord(JsonNode node) {
        LotteryHistory item = new LotteryHistory();
        item.setPeriod(text(node, "period", "issue"));
        item.setDrawDate(LocalDate.parse(text(node, "draw_date", "drawDate")));
        JsonNode redNode = node.get("red_balls");
        if (redNode == null) {
            redNode = node.get("redBalls");
        }
        if (redNode != null && redNode.isArray()) {
            List<String> reds = new ArrayList<>();
            redNode.forEach(n -> reds.add(String.format("%02d", n.asInt())));
            item.setRedBalls(String.join(",", reds));
        } else {
            item.setRedBalls(text(node, "red_balls", "redBalls"));
        }
        item.setBlueBall(String.format("%02d", node.path("blue_ball").asInt(node.path("blueBall").asInt(1))));
        return item;
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asText();
            }
        }
        return "";
    }
}
