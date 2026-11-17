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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        // 1. Check if MASTER_DATA_BASE64 environment variable is provided (Render deployment-safe secret)
        String base64Zip = System.getenv("MASTER_DATA_BASE64");
        if (base64Zip != null && !base64Zip.isBlank() && itemRepository.count() == 0) {
            try {
                log.info("Detected MASTER_DATA_BASE64 environment variable. Importing master data archive...");
                byte[] decoded = Base64.getDecoder().decode(base64Zip.trim());
                importFromZip(new ByteArrayInputStream(decoded));
                if (itemRepository.count() > 0) {
                    log.info("Master data auto-imported successfully from environment variable.");
                    return;
                }
            } catch (Exception e) {
                log.warn("Could not import master data from MASTER_DATA_BASE64: {}", e.getMessage());
            }
        }

        // 2. Check local file paths (local development / testing)
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
        log.info("No master data directory or env archive found. Master data can be uploaded via /api/master-data/upload or web UI.");
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
    public Map<String, Integer> importFromZip(InputStream zipStream) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            Map<String, byte[]> fileMap = new HashMap<>();
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = entry.getName().toLowerCase();
                    byte[] bytes = zis.readAllBytes();
                    fileMap.put(name, bytes);
                }
                zis.closeEntry();
            }

            // Ingest in logical dependency order: plants -> items -> suppliers -> po_history -> contracts -> contract_lines
            for (Map.Entry<String, byte[]> e : fileMap.entrySet()) {
                if (e.getKey().endsWith("plants.csv")) {
                    counts.put("plants", importPlants(new InputStreamReader(new ByteArrayInputStream(e.getValue()), StandardCharsets.UTF_8)));
                }
            }
            for (Map.Entry<String, byte[]> e : fileMap.entrySet()) {
                if (e.getKey().endsWith("item_master.csv")) {
                    counts.put("items", importItems(new InputStreamReader(new ByteArrayInputStream(e.getValue()), StandardCharsets.UTF_8)));
                }
            }
            for (Map.Entry<String, byte[]> e : fileMap.entrySet()) {
                if (e.getKey().endsWith("supplier_master.csv")) {
                    counts.put("suppliers", importSuppliers(new InputStreamReader(new ByteArrayInputStream(e.getValue()), StandardCharsets.UTF_8)));
                }
            }
            for (Map.Entry<String, byte[]> e : fileMap.entrySet()) {
                if (e.getKey().endsWith("po_history.csv")) {
                    counts.put("po_history", importPoHistory(new InputStreamReader(new ByteArrayInputStream(e.getValue()), StandardCharsets.UTF_8)));
                }
            }
            for (Map.Entry<String, byte[]> e : fileMap.entrySet()) {
                if (e.getKey().endsWith("contract_register.csv")) {
                    counts.put("contracts", importContracts(new InputStreamReader(new ByteArrayInputStream(e.getValue()), StandardCharsets.UTF_8)));
                }
            }
            for (Map.Entry<String, byte[]> e : fileMap.entrySet()) {
                if (e.getKey().endsWith("contract_lines.csv")) {
                    counts.put("contract_lines", importContractLines(new InputStreamReader(new ByteArrayInputStream(e.getValue()), StandardCharsets.UTF_8)));
                }
            }
            log.info("Successfully imported master data from ZIP archive: {}", counts);
        } catch (Exception e) {
            log.error("Failed to import master data from ZIP archive: {}", e.getMessage(), e);
        }
        return counts;
    }

    public Map<String, Object> getMasterDataStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        long plantCount = plantRepository.count();
        long itemCount = itemRepository.count();
        long supplierCount = supplierRepository.count();
        long poCount = poHistoryRepository.count();
        long contractCount = contractRepository.count();
        long lineCount = contractLineRepository.count();

        status.put("plants", plantCount);
        status.put("items", itemCount);
        status.put("suppliers", supplierCount);
        status.put("po_history", poCount);
        status.put("contracts", contractCount);
        status.put("contract_lines", lineCount);
        status.put("initialized", itemCount > 0 && supplierCount > 0);
        return status;
    }

    @Transactional
    public int importPlants(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            List<Plant> plants = new ArrayList<>();
            for (CSVRecord rec : parser) {
                plants.add(new Plant(rec.get("plant"), rec.get("name")));
            }
            plantRepository.saveAll(plants);
            return plants.size();
        } catch (Exception e) {
            log.error("Failed to import plants: {}", e.getMessage());
            return 0;
        }
    }

    public int importPlants(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            return importPlants(reader);
        } catch (Exception e) {
            log.error("Failed to read plants from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importItems(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
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
            log.error("Failed to import items: {}", e.getMessage());
            return 0;
        }
    }

    public int importItems(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            return importItems(reader);
        } catch (Exception e) {
            log.error("Failed to read items from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importSuppliers(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
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
            log.error("Failed to import suppliers: {}", e.getMessage());
            return 0;
        }
    }

    public int importSuppliers(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            return importSuppliers(reader);
        } catch (Exception e) {
            log.error("Failed to read suppliers from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importPoHistory(Reader reader) {
        if (poHistoryRepository.count() > 0) {
            log.info("PO history already populated ({} records). Skipping duplicate ingestion.", poHistoryRepository.count());
            return (int) poHistoryRepository.count();
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
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
            log.error("Failed to import po history: {}", e.getMessage());
            return 0;
        }
    }

    public int importPoHistory(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            return importPoHistory(reader);
        } catch (Exception e) {
            log.error("Failed to read po history from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importContracts(Reader reader) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
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
            log.error("Failed to import contracts: {}", e.getMessage());
            return 0;
        }
    }

    public int importContracts(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            return importContracts(reader);
        } catch (Exception e) {
            log.error("Failed to read contracts from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int importContractLines(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            List<ContractLine> lines = new ArrayList<>();
            for (CSVRecord rec : parser) {
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
            log.error("Failed to import contract lines: {}", e.getMessage());
            return 0;
        }
    }

    public int importContractLines(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            return importContractLines(reader);
        } catch (Exception e) {
            log.error("Failed to read contract lines from {}: {}", csvPath, e.getMessage());
            return 0;
        }
    }
}
