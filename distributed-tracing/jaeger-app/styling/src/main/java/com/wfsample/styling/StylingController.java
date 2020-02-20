package com.wfsample.styling;

import com.wfsample.common.dto.DeliveryStatusDTO;
import com.wfsample.common.dto.PackedShirtsDTO;
import com.wfsample.common.dto.ShirtDTO;
import com.wfsample.common.dto.ShirtStyleDTO;
import io.opentracing.Tracer;
import io.opentracing.contrib.spring.web.client.TracingExchangeFilterFunction;
import io.opentracing.contrib.spring.web.client.WebClientSpanDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.stream.Collectors.toList;

@RestController
@RequestMapping("/style")
public class StylingController {

    private static final Logger logger = LoggerFactory.getLogger(StylingController.class);

    private final Tracer tracer;
    private final WebClient webClient;
    private final List<ShirtStyleDTO> shirtStyles = new ArrayList<>();

    public StylingController(Tracer tracer) {
        this.tracer = tracer;

        this.webClient = WebClient.builder()
                .filter(new TracingExchangeFilterFunction(tracer, Collections.singletonList(new WebClientSpanDecorator.StandardTags())))
                .build();

        ShirtStyleDTO style1 = new ShirtStyleDTO();
        style1.setName("style1");
        style1.setImageUrl("style1Image");
        ShirtStyleDTO style2 = new ShirtStyleDTO();
        style2.setName("style2");
        style2.setImageUrl("style2Image");
        shirtStyles.add(style1);
        shirtStyles.add(style2);
    }

    @GetMapping
    public Mono<ResponseEntity> getAllStyles() {
        return Mono.just(ResponseEntity.ok(shirtStyles));
    }

    @GetMapping
    @RequestMapping("/{id}/make")
    public Mono<ResponseEntity> makeShirts(@PathVariable String id, @RequestParam int quantity) {
        if (randomInt(0, 5) == 0) {
            String msg = "Failed to make shirts!";
            logAndTrace(msg);

            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(msg));
        }

        String orderNum = UUID.randomUUID().toString();
        List<ShirtDTO> packedShirts = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            packedShirts.add(new ShirtDTO(new ShirtStyleDTO(id, id + "Image")));
        }

        PackedShirtsDTO packedShirtsDTO = new PackedShirtsDTO(packedShirts
                .stream()
                .map(shirt -> new ShirtDTO(new ShirtStyleDTO(shirt.getStyle().getName(), shirt.getStyle().getImageUrl()))).collect(toList()));

        return this.webClient.post()
                .uri(URI.create("http://localhost:8080/delivery/dispatch/" + orderNum))
                .body(BodyInserters.fromValue(packedShirtsDTO))
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, response -> Mono.just(new RuntimeException("HTTP " + response.rawStatusCode() + ": Failed to make shirts!")))
                .onStatus(HttpStatus::is5xxServerError, response -> Mono.just(new RuntimeException("HTTP " + response.rawStatusCode() + ": Failed to make shirts!")))
                .bodyToMono(DeliveryStatusDTO.class).map(ResponseEntity::ok);
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
