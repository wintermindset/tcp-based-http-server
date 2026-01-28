package com.wintermindset.handler;

public final class HandlerFactory {

    public static Handler fromClassName(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!Handler.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(
                        className + " does not implement Handler"
                );
            }
            return (Handler) clazz
                    .getDeclaredConstructor()
                    .newInstance();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create handler: " + className, e
            );
        }
    }
}
