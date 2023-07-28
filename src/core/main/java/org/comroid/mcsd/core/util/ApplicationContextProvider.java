package org.comroid.mcsd.core.util;

import org.comroid.api.Polyfill;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * brought to you by ChatGPT
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    public static ApplicationContext get() {
        return applicationContext;
    }

    public static <T, R extends T> R bean(Class<T> type) {
        return bean(type, null);
    }

    public static <T, R extends T> R bean(Class<T> type, String name) {
        return Polyfill.uncheckedCast(name == null ? get().getBean(type) : get().getBean(name, type));
    }

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) {
        ApplicationContextProvider.applicationContext = applicationContext;
    }
}
