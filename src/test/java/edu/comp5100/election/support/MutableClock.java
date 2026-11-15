package edu.comp5100.election.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock that tests move by hand, so time-dependent rules are tested without sleeping. */
public class MutableClock extends Clock {

    private volatile Instant current;

    public MutableClock(Instant initial) {
        this.current = initial;
    }

    public void setInstant(Instant instant) {
        this.current = instant;
    }

    public void advance(Duration duration) {
        this.current = current.plus(duration);
    }

    @Override
    public Instant instant() {
        return current;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
