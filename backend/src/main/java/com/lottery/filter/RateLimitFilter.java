package com.lottery.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.common.Result;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PREFIXES = Set.of(
            "/api/v1/predict",
            "/api/v1/admin/sync/trigger",
            "/api/v1/admin/sync/retry"
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter rejectedCounter;

    @Value("${lottery.ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${lottery.ratelimit.predict-per-minute:30}")
    private int predictPerMinute;

    @Value("${lottery.ratelimit.sync-per-minute:10}")
    private int syncPerMinute;

    public RateLimitFilter(StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rejectedCounter = meterRegistry.counter("lottery.ratelimit.rejected");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String bucket = resolveBucket(path);
        if (bucket == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = bucket.startsWith("predict") ? predictPerMinute : syncPerMinute;
        String key = "ratelimit:" + bucket + ":" + clientKey(request);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        if (count != null && count > limit) {
            rejectedCounter.increment();
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.fail(429, "Rate limit exceeded"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolveBucket(String path) {
        for (String prefix : LIMITED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return prefix.contains("predict") ? "predict" : "sync";
            }
        }
        return null;
    }

    private String clientKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && !auth.isBlank()) {
            return auth.hashCode() + "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
