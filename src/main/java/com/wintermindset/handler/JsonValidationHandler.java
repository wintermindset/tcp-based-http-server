package com.wintermindset.handler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;

public class JsonValidationHandler implements Runnable {
    private final Socket client;
    private final HashMap<String, String> localJsonSchemas = new HashMap<>();

    public JsonValidationHandler(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    // Protocol + HTTP and so on
                    boolean valid = true;
                    writer.write(valid ? "VALID\n" : "INVALID\n");
                    writer.flush();
                } catch (Exception e) {
                    writer.write("ERROR: Invalid JSON\n");
                    writer.flush();
                }
            }
        } catch (IOException e) {
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }
}