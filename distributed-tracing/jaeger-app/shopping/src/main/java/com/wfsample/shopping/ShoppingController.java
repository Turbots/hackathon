package com.wfsample.shopping;

import com.wfsample.common.dto.DeliveryStatusDTO;
import com.wfsample.common.dto.OrderDTO;
import com.wfsample.common.dto.ShirtStyleDTO;
import io.opentracing.Tracer;
import io.opentracing.contrib.spring.web.client.TracingExchangeFilterFunction;
import io.opentracing.contrib.spring.web.client.WebClientSpanDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/shop")
public class ShoppingController {

    private static final Logger logger = LoggerFactory.getLogger(ShoppingController.class);

    private final Tracer tracer;
    private final WebClient webClient;

    public ShoppingController(Tracer tracer) {
        this.tracer = tracer;
        this.webClient = WebClient.builder()
                .filter(new TracingExchangeFilterFunction(tracer, Collections.singletonList(new WebClientSpanDecorator.StandardTags())))
                .build();
    }

    @GetMapping
    @RequestMapping("/menu")
    public Flux<ShirtStyleDTO> getShoppingMenu() {
        URI uri = URI.create("http://localhost:8082/style");

        return this.webClient.get()
                .uri(uri.toString())
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, response -> Mono.just(new RuntimeException("HTTP " + response.rawStatusCode() + ": Failed to fetch shopping menu!")))
                .onStatus(HttpStatus::is5xxServerError, response -> Mono.just(new RuntimeException("HTTP " + response.rawStatusCode() + ": Failed to fetch shopping menu!")))
                .bodyToFlux(ShirtStyleDTO.class);
    }

    @PostMapping
    @RequestMapping("/order")
    public Mono<ResponseEntity> orderShirts(@RequestBody Mono<OrderDTO> orderMono) {
        return orderMono.flatMap(orderDTO -> {
            if (ThreadLocalRandom.current().nextInt(0, 10) == 0) {
                String msg = "Failed to order shirts!";
                logAndTrace(msg);

                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(msg));
            }

            URI uri = URI.create("http://localhost:8082/style/" + orderDTO.getStyleName() + "/make?quantity=" + orderDTO.getQuantity());

            return this.webClient.get()
                    .uri(uri.toString())
                    .retrieve()
                    .onStatus(HttpStatus::is4xxClientError, response -> Mono.just(new RuntimeException("HTTP " + response.rawStatusCode() + ": Failed to order shirts!")))
                    .onStatus(HttpStatus::is5xxServerError, response -> Mono.just(new RuntimeException("HTTP " + response.rawStatusCode() + ": Failed to order shirts!")))
                    .bodyToMono(DeliveryStatusDTO.class).map(ResponseEntity::ok);
        });
    }

    private void logAndTrace(String s) {
        String msg = "Failed to dispatch shirts!";
        logger.warn(msg);
        tracer.activeSpan().setTag("error", msg);
    }

    private int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

}
