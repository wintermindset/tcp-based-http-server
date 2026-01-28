package com.wintermindset.config;

import com.wintermindset.handler.CalcHandler;
import com.wintermindset.handler.Handler;

public final class ServerConfig {

    public int port = 8080;
    public Handler handler = new CalcHandler();
}
