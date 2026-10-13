package com.wintermindset;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/** Container readiness probe against the private management listener. */
public final class HealthCheck {
    private HealthCheck() { }

    public static void main(String[] args) {
        int result = 1;
        HttpURLConnection connection = null;
        try {
            int port = args.length == 0 ? 9090 : Integer.parseInt(args[0]);
            connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/readyz")
                    .toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(false);
            result = connection.getResponseCode() == 200 ? 0 : 1;
        } catch (IOException | IllegalArgumentException ignored) {
            result = 1;
        } finally {
            if (connection != null) connection.disconnect();
        }
        System.exit(result);
    }
}
