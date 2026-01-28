package com.wintermindset.http;

import com.wintermindset.handler.Handler;
import com.wintermindset.io.BufferedInput;
import com.wintermindset.io.BufferedOutput;

import java.io.IOException;
import java.net.Socket;

public final class HttpConnection implements Runnable {

    private final Socket socket;
    private final Handler handler;
    private final BufferedInput in;
    private final BufferedOutput out;
    private final HttpRequestParser parser = new HttpRequestParser();

    public HttpConnection(Socket socket, Handler handler) throws IOException {
        this.socket = socket;
        this.handler = handler;
        this.in = new BufferedInput(socket.getInputStream());
        this.out = new BufferedOutput(socket.getOutputStream());
    }

    @Override
    public void run() {
        try (socket) {
            boolean keepAlive = true;
            while (keepAlive) {
                HttpRequest req;
                try {
                    req = parser.parse(in);
                } catch (Exception e) {
                    HttpResponse.badRequest("Bad Request")
                            .writeTo(out, false);
                    break;
                }
                HttpResponse resp;
                try {
                    resp = handler.handle(req);
                } catch (Exception e) {
                    resp = HttpResponse.internalError("Internal Error");
                }
                keepAlive = req.keepAlive();
                resp.writeTo(out, keepAlive);
                if (!keepAlive) {
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }
}
