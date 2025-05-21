package io.seata.sample.storageservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PostConstruct;

@RestController
@RequestMapping("/storage")
public class StorageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate; // Using JdbcTemplate for simplicity

    // Initialize with some sample data
    @PostConstruct
    public void init() {
        jdbcTemplate.update("DROP TABLE IF EXISTS storage_tbl;");
        jdbcTemplate.update("CREATE TABLE storage_tbl (id BIGINT AUTO_INCREMENT PRIMARY KEY, commodity_code VARCHAR(255), count INT);");
        jdbcTemplate.update("INSERT INTO storage_tbl (commodity_code, count) VALUES ('product-1', 100);");
        jdbcTemplate.update("INSERT INTO storage_tbl (commodity_code, count) VALUES ('product-2', 200);");
        LOGGER.info("storage_tbl initialized with sample data.");
    }

    @PostMapping("/deduct")
    public ResponseEntity<Boolean> deductStock(@RequestParam String commodityCode, @RequestParam Integer count) {
        LOGGER.info("Received request to deduct stock for commodity: {}, count: {}", commodityCode, count);
        LOGGER.info("Current XID in StorageService: {}", io.seata.core.context.RootContext.getXID());


        // Simulate a failure condition for testing rollback
        // if ("product-1".equals(commodityCode) && count > 50) {
        //     LOGGER.warn("Simulating failure for product-1 with count > 50");
        //     throw new RuntimeException("Simulated storage deduction failure");
        // }


        String sql = "UPDATE storage_tbl SET count = count - ? WHERE commodity_code = ? AND count >= ?";
        int updatedRows = jdbcTemplate.update(sql, count, commodityCode, count);

        if (updatedRows > 0) {
            LOGGER.info("Stock deducted successfully for commodity: {}", commodityCode);
            return ResponseEntity.ok(true);
        } else {
            LOGGER.error("Failed to deduct stock for commodity: {} (insufficient stock or product not found)", commodityCode);
            // This will cause the transaction to roll back if it's part of a global Seata transaction.
            // Throwing an exception is a common way to signal failure to Seata.
            throw new RuntimeException("Insufficient stock or product not found for " + commodityCode);
            // return ResponseEntity.status(500).body(false); // Or return a specific error response
        }
    }

    // Endpoint to check current stock (for verification)
    @RequestMapping("/get_stock")
    public ResponseEntity<Integer> getStock(@RequestParam String commodityCode) {
        String sql = "SELECT count FROM storage_tbl WHERE commodity_code = ?";
        try {
            Integer stock = jdbcTemplate.queryForObject(sql, new Object[]{commodityCode}, Integer.class);
            return ResponseEntity.ok(stock);
        } catch (Exception e) {
            LOGGER.error("Error fetching stock for {}: {}", commodityCode, e.getMessage());
            return ResponseEntity.status(404).body(null);
        }
    }
}
