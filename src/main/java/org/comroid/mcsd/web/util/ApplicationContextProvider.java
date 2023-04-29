package org.comroid.mcsd.web.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/** brought to you by ChatGPT */
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
