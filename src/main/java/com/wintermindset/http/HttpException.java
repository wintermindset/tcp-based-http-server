package com.wintermindset.http;

import java.io.IOException;

public final class HttpException extends IOException {
    private final int status;

    public HttpException(int status, String reason) {
        super(reason);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
