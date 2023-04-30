package org.comroid.mcsd.web.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** brought to you by ChatGPT */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    public static ApplicationContext get() {
        return applicationContext;
    }

    public static <T> T bean(Class<T> type) {
        return get().getBean(type);
    }

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) {
        ApplicationContextProvider.applicationContext = applicationContext;
    }
}
