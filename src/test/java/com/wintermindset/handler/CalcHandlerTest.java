package com.wintermindset.handler;

import com.wintermindset.http.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CalcHandlerTest {
    private int status(String method, String path) {
        HttpRequest request = new HttpRequest();
        request.method = method;
        request.path = path;
        return new CalcHandler().handle(request).status();
    }

    @Test
    void handlesEncodedAdditionAndHead() {
        assertEquals(200, status("GET", "/calc?x=2&y=3&op=%2B"));
        assertEquals(200, status("HEAD", "/calc?x=2&y=3&op=*"));
        assertEquals(404, status("GET", "/calculator?x=2&y=3&op=*"));
        assertEquals(405, status("POST", "/calc"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/calc", "/calc?x=1&y=0&op=/", "/calc?x=NaN&y=1&op=*",
            "/calc?x=1&x=2&y=3&op=*", "/calc?x=1&y=2&op=%xx",
            "/calc?x=1e308&y=1e308&op=*"
    })
    void rejectsInvalidParameters(String path) {
        assertEquals(400, status("GET", path));
    }

    @Test
    void factoryCreatesOnlyHandlers() {
        assertInstanceOf(CalcHandler.class, HandlerFactory.fromClassName(CalcHandler.class.getName()));
        assertThrows(RuntimeException.class, () -> HandlerFactory.fromClassName(String.class.getName()));
        assertThrows(RuntimeException.class, () -> HandlerFactory.fromClassName("missing.Handler"));
    }
}
