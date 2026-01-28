# TCP based HTTP server

A lightweight HTTP server built on raw TCP, using Java virtual threads for scalable concurrency. Client connections are processed via a handler-based architecture that cleanly separates request handling logic from networking concerns. The server implements a minimal HTTP/1.1 stack, including request parsing, response generation, keep-alive support, and pipelined request processing, without relying on external frameworks. Designed for clarity and control, it serves as a compact foundation for experimenting with low-level HTTP mechanics, custom protocols, and high-concurrency server designs on the JVM.


## Features

- Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor`).
- TCP-based HTTP/1.1 server.
- HTTP keep-alive support.
- HTTP request pipelining.
- Buffered TCP input/output.
- Pluggable request handlers (via configuration).
- Simple calculator example handler.
- YAML-based configuration.
- Zero runtime dependencies.
- Docker-ready.


## Requirements

- Java 25
- Maven 3.9+ (optional)
- Docker (optional)


## Configuration

`config.yaml` example:

``` yaml
port: 8080
handler: net.server.calc.CalcHandler
```


## Running locally

1. Build:

``` bash
mvn clean package
```

2. Run (using builded config):

``` bash
java -jar target/loom-http-server-1.0.0.jar
```

3. Run (using external config):

``` bash
java -jar target/loom-http-server-1.0.0.jar /path/to/config.yaml
```


## Example of work

Request:

``` bash
GET /calc?x=3&y=4&op=* HTTP/1.1
Host: localhost
```

Response:

``` bash
HTTP/1.1 200 OK
Content-Type: text/plain
Content-Length: 2

12
```


## Handler model

Handler must be implemented:

``` Java
public interface HttpHandler {
    HttpResponse handle(HttpRequest request);
}
```

Handlers are instantiated at startup via reflection using the class name provided in the configuration file.


## Tests

Run all tests:

``` bash
mvn test
```

Integration tests start the server and perform real TCP/HTTP requests.


## Docker

Build image:

``` bash
docker build -t tcp-based-http-server .
```

Run container:

``` bash
docker run -p 8080:8080 tcp-based-http-server
```


## License

This project is licensed under the MIT License. See [`LICENSE`](./LICENSE) for details.
