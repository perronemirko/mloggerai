package com.mloggerai.plugin;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class MLoggerAIBundle extends DynamicBundle {

    private static final String BUNDLE = "messages.MLoggerAIBundle";
    private static final MLoggerAIBundle INSTANCE = new MLoggerAIBundle();

    private MLoggerAIBundle() {
        super(BUNDLE);
    }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                 @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}