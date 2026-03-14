package com.wintermindset.handler;

/**
 * Factory responsible for creating {@link Handler} instances
 * using reflection.
 *
 * <p>This class allows dynamic loading of request handlers
 * by their fully qualified class name. The target class must:</p>
 *
 * <ul>
 *     <li>implement the {@link Handler} interface</li>
 *     <li>provide a public no-argument constructor</li>
 * </ul>
 *
 * <p>Typical usage:</p>
 *
 * <pre>
 * Handler handler =
 *     HandlerFactory.fromClassName("com.example.MyHandler");
 * </pre>
 *
 * <p>This approach is commonly used for simple plugin-like
 * architectures where handlers are configured externally
 * (e.g. via configuration files).</p>
 */
public final class HandlerFactory {

    /**
     * Creates a {@link Handler} instance from a class name.
     *
     * <p>The method loads the class using {@link Class#forName(String)}
     * and instantiates it using its default constructor.</p>
     *
     * @param className fully qualified handler class name
     * @return instantiated handler
     *
     * @throws IllegalArgumentException if the class does not implement {@link Handler}
     * @throws RuntimeException if the class cannot be loaded or instantiated
     */
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