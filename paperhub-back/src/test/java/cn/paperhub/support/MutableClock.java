package cn.paperhub.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public class MutableClock extends Clock {
    private Instant now;
    private final ZoneId zone;
    public MutableClock() { this(Instant.parse("2026-11-07T00:00:00Z"), ZoneId.of("Asia/Shanghai")); }
    private MutableClock(Instant now, ZoneId zone) { this.now = now; this.zone = zone; }
    public void advance(Duration duration) { now = now.plus(duration); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new MutableClock(now, zone); }
    @Override public Instant instant() { return now; }
}
