package com.lottery.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.entity.LotteryHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "lottery.sync.feed-type", havingValue = "http")
public class HttpLotteryFeedFetcher implements LotteryFeedFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpLotteryFeedFetcher.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpLotteryFeedFetcher(@Value("${lottery.sync.feed-url:}") String feedUrl,
                                  ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(feedUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String source() {
        return "http-feed";
    }

    @Override
    public List<LotteryHistory> fetch() {
        try {
            String body = restClient.get().uri("").retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.isArray() ? root : root.get("data");
            List<LotteryHistory> records = new ArrayList<>();
            if (items != null) {
                for (JsonNode item : items) {
                    records.add(parseNode(item));
                }
            }
            return records;
        } catch (Exception ex) {
            log.warn("HTTP feed fetch failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private LotteryHistory parseNode(JsonNode node) {
        LotteryHistory record = new LotteryHistory();
        record.setPeriod(node.get("period").asText());
        record.setDrawDate(LocalDate.parse(node.get("draw_date").asText()));
        record.setRedBalls(node.get("red_balls").asText());
        record.setBlueBall(node.get("blue_ball").asText());
        return record;
    }
}
