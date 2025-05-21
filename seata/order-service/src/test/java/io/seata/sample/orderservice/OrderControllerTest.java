package io.seata.sample.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK) // Using MOCK to avoid starting a real server but still load full context
@AutoConfigureMockMvc
public class OrderControllerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageServiceClient storageServiceClient;

    @Autowired
    private ObjectMapper objectMapper; // For converting objects to JSON

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        // Clear any existing XID before each test
        if (RootContext.getXID() != null) {
            RootContext.unbind();
        }
        sampleOrder = new Order();
        sampleOrder.setUserId("test-user");
        sampleOrder.setCommodityCode("product-1");
        sampleOrder.setCount(10);
        sampleOrder.setMoney(100);
    }

    @Test
    void testCreateOrder_Success() throws Exception {
        LOGGER.info("Testing Order Creation - Success Scenario");

        // Mock StorageServiceClient to return true (success)
        when(storageServiceClient.deductStock(anyString(), anyInt())).thenReturn(true);

        MvcResult result = mockMvc.perform(post("/orders/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleOrder)))
                .andExpect(status().isOk())
                .andReturn();

        // Verify that StorageServiceClient.deductStock was called
        verify(storageServiceClient).deductStock(sampleOrder.getCommodityCode(), sampleOrder.getCount());
        LOGGER.info("Response from /orders/create (Success): {}", result.getResponse().getContentAsString());
    }

    @Test
    void testCreateOrder_StorageServiceFailure_ShouldRollback() throws Exception {
        LOGGER.info("Testing Order Creation - Storage Service Failure Scenario (Rollback)");

        // Mock StorageServiceClient to throw an exception
        String exceptionMessage = "Simulated storage deduction failure";
        doThrow(new RuntimeException(exceptionMessage))
                .when(storageServiceClient).deductStock(anyString(), anyInt());
        
        // We expect the controller to throw RuntimeException due to @GlobalTransactional propagating it
        // So, the perform call itself should result in an exception being thrown by MockMvc
        Exception resolvedException = mockMvc.perform(post("/orders/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleOrder)))
                .andExpect(status().isInternalServerError()) // Or whatever status your exception handler returns
                .andReturn().getResolvedException();

        // Verify that StorageServiceClient.deductStock was called
        verify(storageServiceClient).deductStock(sampleOrder.getCommodityCode(), sampleOrder.getCount());

        // Assert that the exception propagated by @GlobalTransactional is the one we expect
        assertNotNull(resolvedException, "Expected an exception to be thrown");
        assertTrue(resolvedException instanceof RuntimeException, "Expected RuntimeException");
        assertTrue(resolvedException.getMessage().contains("Stock deduction failed"), 
                   "Exception message should indicate stock deduction failure leading to rollback.");

        LOGGER.info("Exception caught from /orders/create (Storage Failure): {}", resolvedException.getMessage());
    }
}
