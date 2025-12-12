package com.wintermindset.factory;

import java.net.Socket;

@FunctionalInterface
public interface HandlerFactory {
    Runnable create(Socket client);
}