package com.wintermindset;

import java.io.IOException;

import com.wintermindset.config.ConfigReader;
import com.wintermindset.config.ServerConfig;
import com.wintermindset.core.Server;

public class App {
    
    public static void main(String[] args) {
        try {
            ServerConfig config = ConfigReader.loadFromResources("server-config.yaml");
            Server server = new Server(config);
            server.start();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
