package com.wintermindset.handler;

import com.wintermindset.http.HttpRequest;
import com.wintermindset.http.HttpResponse;

public interface Handler {
    
    HttpResponse handle(HttpRequest req);
}
