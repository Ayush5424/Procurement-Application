package com.cantill.procurement.service;

import com.cantill.procurement.domain.*;
import com.cantill.procurement.repository.*;
import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MasterDataService {
    private static final Logger log = LoggerFactory.getLogger(MasterDataService.class);

    private final PlantRepository plantRepository;
    private final ItemRepository itemRepository;
    private final SupplierRepository supplierRepository;
    private final PoHistoryRepository poHistoryRepository;
    private final ContractRepository contractRepository;
    private final ContractLineRepository contractLineRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final PolicyRuleRepository policyRuleRepository;

    @Value("${app.data-dir:task_kit/Cantill_FullStack_Task_Kit}")
    private String configuredDataDir;

    public MasterDataService(
            PlantRepository plantRepository,
            ItemRepository itemRepository,
            SupplierRepository supplierRepository,
            PoHistoryRepository poHistoryRepository,
            ContractRepository contractRepository,
            ContractLineRepository contractLineRepository,
            PolicyVersionRepository policyVersionRepository,
            PolicyRuleRepository policyRuleRepository) {
        this.plantRepository = plantRepository;
        this.itemRepository = itemRepository;
        this.supplierRepository = supplierRepository;
        this.poHistoryRepository = poHistoryRepository;
        this.contractRepository = contractRepository;
        this.contractLineRepository = contractLineRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.policyRuleRepository = policyRuleRepository;
    }

    @PostConstruct
    public void init() {
        seedInitialPolicyV5();
        autoImportIfDataAvailable();
    }

    @Transactional
    public void seedInitialPolicyV5() {
        if (policyVersionRepository.findByVersionNumber("5.0").isPresent()) {
            log.info("Policy version 5.0 already exists.");
            return;
        }

        log.info("Seeding initial Corvo Procurement Policy v5.0...");
        PolicyVersion v5 = new PolicyVersion(
                "5.0",
                LocalDate.of(2026, 4, 1),
                "ACTIVE",
                "Corvo Engineering Procurement Policy Version 5.0"
        );
        policyVersionRepository.save(v5);

        List<PolicyRule> rules = List.of(
                new PolicyRule("5.0", 1, "5.1", "Capex Sourcing",
                        "The item type is Capex. Needs capex approval.",
                        "SOURCING_REQUEST", "CAPEX_SOURCING",
                        "{\"itemType\": \"Capex\"}", 5, true),

                new PolicyRule("5.0", 2, "5.2", "Applicable Contract",
                        "A contract can be used with sufficient ceiling.",
                        "PO", "CONTRACT_PO",
                        "{\"contractEligible\": true, \"hasCeiling\": true}", 0, false),

                new PolicyRule("5.0", 3, "5.3", "Emergency Purchase",
                        "Emergency, value <= 500,000, and item bought from Active supplier in last 365 days.",
                        "PO", "EMERGENCY_PO",
                        "{\"urgency\": \"Emergency\", \"maxValue\": 500000, \"requiresPastPurchase365d\": true}", 0, false),

                new PolicyRule("5.0", 4, "5.4", "Spot Purchase",
                        "Value below 200,000, and item bought from Active supplier in last 365 days.",
                        "PO", "SPOT_PO",
                        "{\"maxValueExclusive\": 200000, \"requiresPastPurchase365d\": true}", 0, false),

                new PolicyRule("5.0", 5, "5.5", "RFQ Sourcing",
                        "Value below 1,000,000 (including small items never bought in last 365 days).",
                        "SOURCING_REQUEST", "RFQ",
                        "{\"maxValueExclusive\": 1000000}", 3, false),

                new PolicyRule("5.0", 6, "5.6", "RFP Sourcing",
                        "Value 1,000,000 or more.",
                        "SOURCING_REQUEST", "RFP",
                        "{\"minValue\": 1000000}", 5, false)
        );

        policyRuleRepository.saveAll(rules);
        log.info("Successfully seeded Corvo Procurement Policy v5.0 with {} rules.", rules.size());
    }

    public void autoImportIfDataAvailable() {
        List<String> candidatePaths = List.of(
                configuredDataDir,
                "task_kit/Cantill_FullStack_Task_Kit",
                "../task_kit/Cantill_FullStack_Task_Kit",
                "./task_kit/Cantill_FullStack_Task_Kit",
                "C:/Users/dell/Downloads/Procurement Application/task_kit/Cantill_FullStack_Task_Kit"
        );

        for (String p : candidatePaths) {
            Path path = Paths.get(p);
            if (Files.exists(path) && Files.isDirectory(path)) {
                log.info("Found master data directory at: {}", path.toAbsolutePath());
                importFromDirectory(path);
                return;
            }
        }
        log.info("No master data directory found automatically. Ready for upload or API import.");
    }

    @Transactional
    public Map<String, Integer> importFromDirectory(Path baseDir) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        Path plantsPath = baseDir.resolve("master_data/plants.csv");
        if (Files.exists(plantsPath)) {
            counts.put("plants", importPlants(plantsPath));
        }

        Path itemsPath = baseDir.resolve("master_data/item_master.csv");
        if (Files.exists(itemsPath)) {
            counts.put("items", importItems(itemsPath));
        }

        Path suppliersPath = baseDir.resolve("master_data/supplier_master.csv");
        if (Files.exists(suppliersPath)) {
            counts.put("suppliers", importSuppliers(suppliersPath));
        }

        Path poHistoryPath = baseDir.resolve("master_data/po_history.csv");
        if (Files.exists(poHistoryPath)) {
            counts.put("po_history", importPoHistory(poHistoryPath));
        }

        Path contractRegPath = baseDir.resolve("contracts/contract_register.csv");
        if (Files.exists(contractRegPath)) {
            counts.put("contracts", importContracts(contractRegPath));
        }

        Path contractLinesPath = baseDir.resolve("contracts/contract_lines.csv");
        if (Files.exists(contractLinesPath)) {
            counts.put("contract_lines", importContractLines(contractLinesPath));
        }

        log.info("Master data import finished: {}", counts);
        return counts;
    }

    @Transactional
    public int importPlants(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            List<Plant> plants = new ArrayList<>();
            for (CSVRecord rec : parser) {
                plants.add(new Plant(rec.get("plant"), rec.get("name")));
            }
            plantRepository.saveAll(plants);
            return plants.size();
        } catch (Exception e) {
            log.error("Failed to import plants from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importItems(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            List<Item> items = new ArrayList<>();
            for (CSVRecord rec : parser) {
                items.add(new Item(
                        rec.get("item_code"),
                        rec.get("description"),
                        rec.get("uom"),
                        rec.get("category"),
                        rec.get("item_type"),
                        new BigDecimal(rec.get("standard_price"))
                ));
            }
            itemRepository.saveAll(items);
            return items.size();
        } catch (Exception e) {
            log.error("Failed to import items from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importSuppliers(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            List<Supplier> suppliers = new ArrayList<>();
            for (CSVRecord rec : parser) {
                suppliers.add(new Supplier(
                        rec.get("supplier_id"),
                        rec.get("name"),
                        rec.get("city"),
                        rec.get("categories"),
                        rec.get("status"),
                        new BigDecimal(rec.get("on_time_pct")),
                        new BigDecimal(rec.get("quality_rating"))
                ));
            }
            supplierRepository.saveAll(suppliers);
            return suppliers.size();
        } catch (Exception e) {
            log.error("Failed to import suppliers from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importPoHistory(Path csvPath) {
        if (poHistoryRepository.count() > 0) {
            log.info("PO history already populated ({} records). Skipping duplicate ingestion.", poHistoryRepository.count());
            return (int) poHistoryRepository.count();
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            List<PoHistory> poList = new ArrayList<>();
            for (CSVRecord rec : parser) {
                poList.add(new PoHistory(
                        rec.get("po_number"),
                        LocalDate.parse(rec.get("po_date"), dtf),
                        rec.get("supplier_id"),
                        rec.get("item_code"),
                        new BigDecimal(rec.get("quantity")),
                        rec.get("uom"),
                        new BigDecimal(rec.get("unit_price")),
                        rec.get("plant"),
                        Boolean.parseBoolean(rec.get("delivered_on_time"))
                ));
            }
            poHistoryRepository.saveAll(poList);
            return poList.size();
        } catch (Exception e) {
            log.error("Failed to import po history from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importContracts(Path csvPath) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            List<Contract> contracts = new ArrayList<>();
            for (CSVRecord rec : parser) {
                contracts.add(new Contract(
                        rec.get("contract_id"),
                        rec.get("supplier_id"),
                        rec.get("supplier_name"),
                        LocalDate.parse(rec.get("start_date"), dtf),
                        LocalDate.parse(rec.get("end_date"), dtf),
                        new BigDecimal(rec.get("value_cap")),
                        new BigDecimal(rec.get("consumed_value")),
                        "REGISTER",
                        true
                ));
            }
            contractRepository.saveAll(contracts);
            return contracts.size();
        } catch (Exception e) {
            log.error("Failed to import contracts from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importContractLines(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            List<ContractLine> lines = new ArrayList<>();
            for (CSVRecord rec : parser) {
                // Check if already exists to make repeatable
                String contractId = rec.get("contract_id");
                String itemCode = rec.get("item_code");
                if (contractLineRepository.findByContractIdAndItemCode(contractId, itemCode).isEmpty()) {
                    lines.add(new ContractLine(
                            contractId,
                            itemCode,
                            rec.get("description"),
                            rec.get("uom"),
                            new BigDecimal(rec.get("rate"))
                    ));
                }
            }
            contractLineRepository.saveAll(lines);
            return lines.size();
        } catch (Exception e) {
            log.error("Failed to import contract lines from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }
}
