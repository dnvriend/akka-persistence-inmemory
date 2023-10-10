package io.github.alstanchev.pekko.persistence.inmemory.util;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

public class UUIDUtil {
    public static final long START_EPOCH = makeEpoch();
    public static final AtomicLong lastTimestamp = new AtomicLong(0L);

    public static long getCurrentTimestamp() {
        while (true) {
            long now = fromUnixTimestamp(System.currentTimeMillis());
            long last = lastTimestamp.get();
            if (now > last) {
                if (lastTimestamp.compareAndSet(last, now))
                    return now;
            } else {
                long lastMillis = millisOf(last);
                // If the clock went back in time, bail out
                if (millisOf(now) < millisOf(last))
                    return lastTimestamp.incrementAndGet();

                long candidate = last + 1;
                // If we've generated more than 10k uuid in that millisecond,
                // we restart the whole process until we get to the next millis.
                // Otherwise, we try use our candidate ... unless we've been
                // beaten by another thread in which case we try again.
                if (millisOf(candidate) == lastMillis && lastTimestamp.compareAndSet(last, candidate))
                    return candidate;
            }
        }
    }

    public static long makeEpoch() {
        // UUID v1 timestamp must be in 100-nanoseconds interval since 00:00:00.000 15 Oct 1582.
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT-0"));
        c.set(Calendar.YEAR, 1582);
        c.set(Calendar.MONTH, Calendar.OCTOBER);
        c.set(Calendar.DAY_OF_MONTH, 15);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long fromUnixTimestamp(long tstamp) {
        return (tstamp - START_EPOCH) * 10000;
    }

    public static long millisOf(long timestamp) {
        return timestamp / 10000;
    }
}
