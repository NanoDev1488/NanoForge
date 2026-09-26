package ru.nanodev.nanoforge.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ограничивает количество action "console" (и им подобных) в секунду - без
 * этого одна опечатка (например, delay с самим собой по кругу, или confirm без
 * key, из-за чего кнопка триггерится чаще, чем задумано) может тихо утопить
 * консоль сервера сотнями команд в секунду. Порог сознательно высокий (100/сек)
 * - это не защита от вменяемого использования, а страховка от совсем явного бага.
 */
public final class ConsoleRateLimiter {

    public static final int MAX_PER_SECOND = 100;

    private static volatile long windowStartMillis = System.currentTimeMillis();
    private static final AtomicInteger COUNT_IN_WINDOW = new AtomicInteger(0);

    private ConsoleRateLimiter() {
    }

    /** true - можно выполнять команду; false - лимит на эту секунду исчерпан. */
    public static synchronized boolean allow() {
        long now = System.currentTimeMillis();
        if (now - windowStartMillis >= 1000) {
            windowStartMillis = now;
            COUNT_IN_WINDOW.set(0);
        }
        return COUNT_IN_WINDOW.incrementAndGet() <= MAX_PER_SECOND;
    }

    /** Только для тестов - сбрасывает окно, чтобы тесты не зависели от реального времени/порядка запуска. */
    public static synchronized void resetForTests() {
        windowStartMillis = System.currentTimeMillis();
        COUNT_IN_WINDOW.set(0);
    }
}

// by t.me/NanoDev_mc
