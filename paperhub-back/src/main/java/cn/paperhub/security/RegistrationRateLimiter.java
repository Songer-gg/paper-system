package cn.paperhub.security;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Independent, bounded direct-IP limits; registration must not exhaust login buckets. */
@Component
public class RegistrationRateLimiter {
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final SecurityProperties.Registration limits;
    private final Clock clock;
    public RegistrationRateLimiter(SecurityProperties properties, Clock clock) {
        this.limits = properties.getRegistration(); this.clock = clock;
        if (limits.getWindow() == null || limits.getWindow().toMillis() < 1)
            throw new IllegalStateException("Registration rate window must be positive");
    }
    public synchronized void acquire(String ip) {
        long now = clock.millis();
        buckets.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        Bucket bucket = buckets.get(ip);
        if ((bucket != null && bucket.count >= limits.getPerIp())
                || (bucket == null && buckets.size() >= limits.getMaxEntries()))
            throw new BusinessException(ErrorCode.BIZ_REJECTED, "注册尝试过于频繁，请稍后重试");
        if (bucket == null) {
            bucket = new Bucket(now + limits.getWindow().toMillis()); buckets.put(ip, bucket);
        }
        bucket.count++;
    }
    private static final class Bucket {
        private final long expiresAt;
        private int count;
        private Bucket(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
