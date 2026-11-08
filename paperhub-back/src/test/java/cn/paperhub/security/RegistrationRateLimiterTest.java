package cn.paperhub.security;

import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationRateLimiterTest {
    @Test void limitsAttemptsPerIpAndAllowsRetryAfterWindow() {
        MutableClock clock = new MutableClock();
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(new SecurityProperties(), clock);
        for (int i = 0; i < 5; i++) limiter.acquire("127.0.0.1");
        assertThrows(BusinessException.class, () -> limiter.acquire("127.0.0.1"));
        assertDoesNotThrow(() -> limiter.acquire("127.0.0.2"));
        clock.advance(Duration.ofMinutes(1)); assertDoesNotThrow(() -> limiter.acquire("127.0.0.1"));
    }
    @Test void boundsMemoryAndReclaimsExpiredEntries() {
        SecurityProperties properties = new SecurityProperties(); properties.getRegistration().setMaxEntries(1);
        MutableClock clock = new MutableClock(); RegistrationRateLimiter limiter = new RegistrationRateLimiter(properties, clock);
        limiter.acquire("a"); assertThrows(BusinessException.class, () -> limiter.acquire("b"));
        clock.advance(Duration.ofMinutes(1)); assertDoesNotThrow(() -> limiter.acquire("b"));
    }
    @Test void invalidWindowFailsAtStartup() {
        SecurityProperties properties = new SecurityProperties(); properties.getRegistration().setWindow(Duration.ZERO);
        assertThrows(IllegalStateException.class, () -> new RegistrationRateLimiter(properties, new MutableClock()));
    }
}
