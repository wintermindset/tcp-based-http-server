package com.wintermindset;

import com.wintermindset.factory.HandlerFactory;
import com.wintermindset.factory.JsonValidationHandlerFactory;

public class Main {
    public static void main(String[] args) {
        HandlerFactory jsonHandlerFactory = new JsonValidationHandlerFactory();
        Server server = new Server(3000, jsonHandlerFactory);
        server.start();
    }
}