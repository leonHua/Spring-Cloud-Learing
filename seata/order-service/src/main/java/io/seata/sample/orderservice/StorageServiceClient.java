package io.seata.sample.orderservice;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "storage-service", url = "${storage-service-url}") // Name matches Feign client config in application.yml
public interface StorageServiceClient {

    @PostMapping("/storage/deduct")
    boolean deductStock(@RequestParam("commodityCode") String commodityCode, @RequestParam("count") Integer count);

    // You might also have a DTO for the request body if it's more complex
    // For example:
    // @PostMapping("/storage/deduct")
    // boolean deductStock(@RequestBody StorageDeductRequest request);
}

// Example DTO if using a request body:
// class StorageDeductRequest {
//     private String commodityCode;
//     private Integer count;
//     // getters and setters
// }
