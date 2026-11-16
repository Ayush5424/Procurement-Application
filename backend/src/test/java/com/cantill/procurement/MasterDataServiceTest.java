package com.cantill.procurement;

import com.cantill.procurement.domain.PolicyRule;
import com.cantill.procurement.domain.PolicyVersion;
import com.cantill.procurement.repository.*;
import com.cantill.procurement.service.MasterDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MasterDataServiceTest {

    @Autowired
    private MasterDataService masterDataService;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PoHistoryRepository poHistoryRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private PolicyVersionRepository policyVersionRepository;

    @Autowired
    private PolicyRuleRepository policyRuleRepository;

    @Test
    void testPolicyV5Seeded() {
        Optional<PolicyVersion> v5 = policyVersionRepository.findByVersionNumber("5.0");
        assertTrue(v5.isPresent(), "Policy v5.0 should be seeded");
        assertEquals("ACTIVE", v5.get().getStatus());

        List<PolicyRule> rules = policyRuleRepository.findByPolicyVersionNumberOrderByPriorityAsc("5.0");
        assertEquals(6, rules.size(), "Policy v5.0 should have 6 rules");
        assertEquals("5.1", rules.get(0).getRuleCode());
        assertEquals("5.6", rules.get(5).getRuleCode());
    }

    @Test
    void testMasterDataLoaded() {
        assertTrue(plantRepository.count() >= 2, "Should have loaded plants (at least CHK1 and PUN1)");
        assertTrue(itemRepository.count() >= 70, "Should have loaded item master");
        assertTrue(supplierRepository.count() >= 40, "Should have loaded supplier master");
        assertTrue(poHistoryRepository.count() >= 400, "Should have loaded PO history");
        assertTrue(contractRepository.count() >= 10, "Should have loaded registered contracts");
    }
}
