package cn.paperhub.security;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final SecurityProperties.Login limits;
    private final Clock clock;
    public LoginRateLimiter(SecurityProperties properties, Clock clock) {
        this.limits = properties.getLogin();
        this.clock = clock;
        if (limits.getWindow() == null || limits.getWindow().toMillis() < 1) {
            throw new IllegalStateException("Login rate window must be positive");
        }
    }
    public synchronized void acquire(String ip, String login) {
        long now = clock.millis();
        buckets.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        String ipKey = "ip:" + ip;
        String accountKey = "account:" + login.trim().toLowerCase(Locale.ROOT);
        Bucket ipBucket = buckets.get(ipKey);
        Bucket accountBucket = buckets.get(accountKey);
        int newEntries = (ipBucket == null ? 1 : 0) + (accountBucket == null ? 1 : 0);
        if ((ipBucket != null && ipBucket.count >= limits.getPerIp())
                || (accountBucket != null && accountBucket.count >= limits.getPerAccount())
                || buckets.size() + newEntries > limits.getMaxEntries()) {
            throw new BusinessException(ErrorCode.BIZ_REJECTED, "登录尝试过于频繁，请稍后重试");
        }
        if (ipBucket == null) { ipBucket = new Bucket(now + limits.getWindow().toMillis()); buckets.put(ipKey, ipBucket); }
        if (accountBucket == null) { accountBucket = new Bucket(now + limits.getWindow().toMillis()); buckets.put(accountKey, accountBucket); }
        ipBucket.count++;
        accountBucket.count++;
    }
    private static final class Bucket {
        private final long expiresAt;
        private int count;
        private Bucket(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
