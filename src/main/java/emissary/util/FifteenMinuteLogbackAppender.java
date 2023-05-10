package emissary.util;

import ch.qos.logback.core.rolling.RollingFileAppender;

public class FifteenMinuteLogbackAppender<E> extends RollingFileAppender<E> {
    private static long start = System.currentTimeMillis(); // minutes
    private int rollOverTimeInMinutes = 15;

    @Override
    public void rollover() {
        long currentTime = System.currentTimeMillis();
        int maxIntervalSinceLastLoggingInMillis = rollOverTimeInMinutes * 60 * 1000;

        if ((currentTime - start) >= maxIntervalSinceLastLoggingInMillis) {
            super.rollover();
            start = System.currentTimeMillis();
        }
    }
}
