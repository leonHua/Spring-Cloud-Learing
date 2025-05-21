package io.seata.sample.orderservice;

import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private StorageServiceClient storageServiceClient;

    // In a real application, you would have a service layer and persist the order to a database.
    // For simplicity, we'll just log and simulate.

    @PostMapping("/create")
    @GlobalTransactional // This annotation starts the Seata global transaction
    public ResponseEntity<String> createOrder(@RequestBody Order orderRequest) {
        LOGGER.info("Received request to create order: {}", orderRequest.getCommodityCode());
        LOGGER.info("Current XID: {}", io.seata.core.context.RootContext.getXID()); // Log XID

        // 1. Simulate creating order (e.g., save to database)
        // For this example, we're not actually saving the order to keep it simple.
        // In a real app: orderRepository.save(order);
        LOGGER.info("Order created for commodity: {}, count: {}", orderRequest.getCommodityCode(), orderRequest.getCount());

        // 2. Call storage service to deduct stock
        LOGGER.info("Calling storage service to deduct stock for commodity: {}", orderRequest.getCommodityCode());
        boolean stockDeducted = storageServiceClient.deductStock(orderRequest.getCommodityCode(), orderRequest.getCount());

        if (stockDeducted) {
            LOGGER.info("Stock deducted successfully for commodity: {}", orderRequest.getCommodityCode());
            // If stock deduction is successful, the transaction will commit.
            // If it fails, or if any other part of this method throws an exception,
            // Seata will attempt to roll back the transaction.
            return ResponseEntity.ok("Order created and stock deducted successfully.");
        } else {
            LOGGER.error("Failed to deduct stock for commodity: {}. Initiating rollback.", orderRequest.getCommodityCode());
            // You might want to throw an exception here to ensure Seata triggers a rollback.
            // For example: throw new RuntimeException("Stock deduction failed");
            // Seata typically rolls back on unhandled exceptions.
            // If deductStock returns false, we can signal failure.
            // Depending on Seata's configuration, returning a non-2xx response might also trigger rollback.
            // For explicit rollback, throwing an exception is common.
            throw new RuntimeException("Stock deduction failed, transaction will be rolled back.");
            // return ResponseEntity.status(500).body("Failed to deduct stock. Order creation failed.");
        }
    }
}
