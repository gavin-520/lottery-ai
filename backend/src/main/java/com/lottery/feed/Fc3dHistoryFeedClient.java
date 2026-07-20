package com.lottery.feed;

import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.util.Fc3dBallUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches FC3D history from 17500 plain-text feed.
 * Line format: issue yyyy-MM-dd d1 d2 d3 ...
 */
@Component
public class Fc3dHistoryFeedClient {

    private static final Logger log = LoggerFactory.getLogger(Fc3dHistoryFeedClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${lottery.fc3d.sync.feed-url:http://data.17500.cn/3d_desc.txt}")
    private String feedUrl;

    @Value("${lottery.fc3d.sync.timeout-ms:30000}")
    private int timeoutMs;

    public String source() {
        return "fc3d-17500";
    }

    public List<Fc3dDrawEntity> fetchAll() {
        return fetch(Integer.MAX_VALUE);
    }

    /**
     * @param maxLines max newest lines to parse (file is newest-first)
     */
    public List<Fc3dDrawEntity> fetch(int maxLines) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "LotteryAI-FC3D-Sync/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new FeedFetchException("FC3D feed HTTP " + response.statusCode(), "HTTP_ERROR", response.statusCode());
            }
            return parse(response.body(), maxLines);
        } catch (FeedFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("FC3D feed fetch failed: {}", ex.getMessage());
            throw new FeedFetchException("FC3D feed fetch failed: " + ex.getMessage(), "NETWORK_ERROR", 0);
        }
    }

    List<Fc3dDrawEntity> parse(String body, int maxLines) {
        List<Fc3dDrawEntity> rows = new ArrayList<>();
        String[] lines = body.split("\\R");
        int limit = Math.max(1, maxLines);
        for (String line : lines) {
            if (rows.size() >= limit) {
                break;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 5 || !parts[0].chars().allMatch(Character::isDigit)) {
                continue;
            }
            try {
                int d1 = Integer.parseInt(parts[2]);
                int d2 = Integer.parseInt(parts[3]);
                int d3 = Integer.parseInt(parts[4]);
                if (d1 < 0 || d1 > 9 || d2 < 0 || d2 > 9 || d3 < 0 || d3 > 9) {
                    continue;
                }
                Fc3dDrawEntity entity = new Fc3dDrawEntity();
                entity.setIssue(parts[0]);
                entity.setDigit1(d1);
                entity.setDigit2(d2);
                entity.setDigit3(d3);
                entity.setSumValue(Fc3dBallUtils.sum(d1, d2, d3));
                entity.setSpanValue(Fc3dBallUtils.span(d1, d2, d3));
                entity.setOddEvenPattern(Fc3dBallUtils.oddEvenPattern(d1, d2, d3));
                entity.setLotteryType("FC3D");
                if (parts[1].length() >= 10) {
                    entity.setDrawDate(LocalDate.parse(parts[1].substring(0, 10)));
                }
                rows.add(entity);
            } catch (Exception ignored) {
                // skip malformed line
            }
        }
        return rows;
    }
}
