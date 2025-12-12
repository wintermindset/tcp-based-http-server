package com.wintermindset.factory;

import java.net.Socket;

import com.wintermindset.handler.JsonValidationHandler;

public class JsonValidationHandlerFactory implements HandlerFactory {
    @Override
    public Runnable create(Socket client) {
        return new JsonValidationHandler(client);
    }
}