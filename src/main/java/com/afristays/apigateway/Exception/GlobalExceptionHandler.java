package com.afristays.apigateway.Exception;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

    @Configuration
    @Order(-1)
    public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
        return Mono.error(ex);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String errorMessage;
        HttpStatus status;

        if (ex instanceof java.net.ConnectException) {
        status = HttpStatus.SERVICE_UNAVAILABLE;
        errorMessage = "Service temporarily unavailable";
        } else {
        status = HttpStatus.INTERNAL_SERVER_ERROR;
        errorMessage = "Internal server error";
        }

        response.setStatusCode(status);

        String body = String.format("{\"error\":\"%s\",\"status\":%d}", errorMessage, status.value());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());

        return response.writeWith(Mono.just(buffer));
        }
        }