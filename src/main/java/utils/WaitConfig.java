package utils;

import java.time.Duration;

public final class WaitConfig {
    public static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration AJAX_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration ELEMENT_VISIBLE_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration ELEMENT_CLICKABLE_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration POLLING_INTERVAL = Duration.ofMillis(500);

    private WaitConfig() {}
}