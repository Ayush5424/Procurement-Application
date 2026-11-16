package com.cantill.procurement;

import com.cantill.procurement.domain.Item;
import com.cantill.procurement.domain.PurchaseRequisition;
import com.cantill.procurement.repository.PurchaseRequisitionRepository;
import com.cantill.procurement.service.EmployeeChatService;
import com.cantill.procurement.service.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EmployeeChatServiceTest {

    @Autowired
    private EmployeeChatService employeeChatService;

    @Autowired
    private MasterDataService masterDataService;

    @Autowired
    private PurchaseRequisitionRepository purchaseRequisitionRepository;

    @BeforeEach
    void setUp() {
        masterDataService.autoImportIfDataAvailable();
    }

    @Test
    void testStandardChatPrCreationFlow() {
        String sessionId = "test-session-1";

        // Step 1: User says: "Need 200 nitrile gloves for Chakan by next Friday"
        EmployeeChatService.ChatResponseDto step1 = employeeChatService.processMessage(
                sessionId,
                "Need 200 nitrile gloves for Chakan by next Friday"
        );

        assertNotNull(step1.reply());
        assertEquals("CHK1", step1.session().getDraftPlant());
        assertEquals(200, step1.session().getDraftQuantity().intValue());
        assertEquals("CONFIRMATION_PENDING", step1.status());
        assertTrue(step1.reply().contains("summary"), "Should show summary asking confirmation");

        // Step 2: User confirms: "Yes, please proceed"
        EmployeeChatService.ChatResponseDto step2 = employeeChatService.processMessage(
                sessionId,
                "Yes, please proceed"
        );

        assertEquals("COMPLETED", step2.status());
        assertNotNull(step2.createdPr(), "PR should be created");
        assertTrue(purchaseRequisitionRepository.existsById(step2.createdPr().getPrNo()));
    }

    @Test
    void testUnknownItemAsksEstimatedPrice() {
        String sessionId = "test-session-2";

        // Step 1: User asks for a custom unknown item
        EmployeeChatService.ChatResponseDto step1 = employeeChatService.processMessage(
                sessionId,
                "Need 1 Industrial Holographic Projector for Pune"
        );

        // Should detect plant PUN1, quantity 1, but NEW item code and ask for estimated price
        assertEquals("PUN1", step1.session().getDraftPlant());
        assertEquals(1, step1.session().getDraftQuantity().intValue());
        assertEquals("NEW", step1.session().getDraftItemCode());
        assertTrue(step1.reply().toLowerCase().contains("estimated unit price") || step1.reply().toLowerCase().contains("need-by date"));
    }
}
