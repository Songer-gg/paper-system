package cn.paperhub.security;

import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {
    @Test void limitsNormalizeAccountsAndExpire() {
        SecurityProperties props = new SecurityProperties();
        props.getLogin().setPerAccount(1);
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(props, clock);
        limiter.acquire("127.0.0.1", " Researcher ");
        assertThrows(BusinessException.class, () -> limiter.acquire("127.0.0.2", "researcher"));
        clock.advance(Duration.ofMinutes(2));
        assertDoesNotThrow(() -> limiter.acquire("127.0.0.2", "researcher"));
    }
    @Test void capsMemoryAndLimitsIpAcrossDifferentAccounts() {
        SecurityProperties props = new SecurityProperties();
        props.getLogin().setPerIp(1);
        props.getLogin().setMaxEntries(2);
        LoginRateLimiter limiter = new LoginRateLimiter(props, new MutableClock());
        limiter.acquire("127.0.0.1", "first");
        assertThrows(BusinessException.class, () -> limiter.acquire("127.0.0.1", "second"));
        assertThrows(BusinessException.class, () -> limiter.acquire("127.0.0.2", "second"));
    }
}
