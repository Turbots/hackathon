package com.wfsample.delivery;

import com.wfsample.common.dto.DeliveryStatusDTO;
import com.wfsample.common.dto.PackedShirtsDTO;
import io.opentracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/delivery")
public class DeliveryController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);

    private final Tracer tracer;
    private final static Queue<PackedShirtsDTO> dispatchQueue = new ConcurrentLinkedDeque<>();

    public DeliveryController(Tracer tracer) {
        this.tracer = tracer;
    }

    @PostMapping
    @RequestMapping("/dispatch/{orderNum}")
    public Mono<ResponseEntity> getDeliveryStatus(@PathVariable String orderNum, @RequestBody Mono<PackedShirtsDTO> shirtsMono) {
        return shirtsMono.map(shirts -> {
            if (randomInt(0, 5) == 0) {
                String msg = "Failed to dispatch shirts!";
                logAndTrace(msg);

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(msg);
            }
            if (randomInt(0, 10) == 0 || orderNum.isEmpty()) {
                String msg = "Invalid Order Num";
                logAndTrace(msg);

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg);
            }

            if (randomInt(0, 10) == 0 || shirts == null || shirts.getShirts() == null || shirts.getShirts().size() == 0) {
                String msg = "No shirts to deliver";
                logAndTrace(msg);

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg);
            }

            dispatchQueue.add(shirts);
            String trackingNum = UUID.randomUUID().toString();

            logger.info("Tracking number of Order:" + orderNum + " is " + trackingNum);

            return ResponseEntity.ok(new DeliveryStatusDTO(orderNum, trackingNum, "shirts delivery dispatched"));
        });
    }

    @PostMapping
    @RequestMapping("/return/{orderNum}")
    public Mono<ResponseEntity> retrieveOrder(@PathVariable String orderNum) {
        if (orderNum.isEmpty()) {
            String msg = "Invalid Order Num";
            logAndTrace(msg);

            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg));
        }

        return Mono.just(ResponseEntity.ok("Order: " + orderNum + " returned"));
    }

    @Scheduled(fixedRate = 30000)
    public void clearQueue() {
        logger.info("Processing {} in the Dispatch Queue!", dispatchQueue.size());
        while (!dispatchQueue.isEmpty()) {
            deliverPackedShirts(dispatchQueue.poll());
        }
    }

    private void deliverPackedShirts(PackedShirtsDTO packedShirtsDTO) {
        packedShirtsDTO.getShirts().forEach(shirt -> {
            logger.info("Delivering shirt {}", shirt);
        });
        logger.info("{} shirts delivered!", packedShirtsDTO.getShirts().size());
    }

    private void logAndTrace(String msg) {
        logger.warn(msg);
        tracer.activeSpan().setTag("error", msg);
    }

    private int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }
}
