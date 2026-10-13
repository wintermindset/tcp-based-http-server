package com.wintermindset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppStartupTest {
    @TempDir
    Path directory;

    @Test
    void returnsFailureForInvalidArgumentsAndMissingConfig() {
        assertEquals(1, App.run(new String[]{"one", "two"}));
        assertEquals(1, App.run(new String[]{directory.resolve("missing.yaml").toString()}));
    }

    @Test
    void returnsFailureWhenPortIsInUse() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            Path path = directory.resolve("server.yaml");
            Files.writeString(path, "port: " + occupied.getLocalPort() + "\nhandler: com.wintermindset.handler.CalcHandler\n");
            assertEquals(1, App.run(new String[]{path.toString()}));
        }
    }
}
