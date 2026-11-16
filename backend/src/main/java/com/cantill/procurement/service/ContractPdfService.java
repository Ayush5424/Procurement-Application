package com.cantill.procurement.service;

import com.cantill.procurement.domain.Contract;
import com.cantill.procurement.domain.ContractLine;
import com.cantill.procurement.repository.ContractLineRepository;
import com.cantill.procurement.repository.ContractRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContractPdfService {
    private static final Logger log = LoggerFactory.getLogger(ContractPdfService.class);

    private final ContractRepository contractRepository;
    private final ContractLineRepository contractLineRepository;

    public record ExtractedContractLine(String itemCode, String description, String uom, BigDecimal rate) {}

    public record ExtractedContractDto(
            String contractId,
            String supplierId,
            String supplierName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal valueCap,
            BigDecimal consumedValue,
            List<ExtractedContractLine> lines,
            String rawText,
            boolean isValid
    ) {}

    public ContractPdfService(ContractRepository contractRepository, ContractLineRepository contractLineRepository) {
        this.contractRepository = contractRepository;
        this.contractLineRepository = contractLineRepository;
    }

    public ExtractedContractDto extractContractFromPdf(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            return parseContractText(rawText);
        }
    }

    public ExtractedContractDto extractContractFromPdfFile(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            return parseContractText(rawText);
        }
    }

    public ExtractedContractDto parseContractText(String rawText) {
        String contractId = "";
        String supplierId = "";
        String supplierName = "";
        LocalDate startDate = null;
        LocalDate endDate = null;
        BigDecimal valueCap = BigDecimal.ZERO;
        List<ExtractedContractLine> lines = new ArrayList<>();

        // 1. Contract ID (e.g. Contract No. RC-2026-031)
        Pattern cIdPat = Pattern.compile("Contract\\s+(?:No\\.?|Number)\\s*[:\\s]*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);
        Matcher cIdMat = cIdPat.matcher(rawText);
        if (cIdMat.find()) {
            contractId = cIdMat.group(1).trim();
        }

        // 2. Supplier Name and ID (e.g. Pneumatic Point, Pune (Supplier ID S021))
        Pattern suppPat = Pattern.compile("and\\s+([^(]+?)\\s*\\(Supplier\\s+ID\\s+([A-Z0-9]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher suppMat = suppPat.matcher(rawText);
        if (suppMat.find()) {
            supplierName = suppMat.group(1).replaceAll(",\\s*Pune", "").replaceAll(",.*", "").trim();
            supplierId = suppMat.group(2).trim();
        }

        // 3. Valid dates (e.g. valid from 2026-11-15 to 2027-11-14)
        Pattern datePat = Pattern.compile("valid\\s+from\\s+(\\d{4}-\\d{2}-\\d{2})\\s+to\\s+(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE);
        Matcher dateMat = datePat.matcher(rawText);
        if (dateMat.find()) {
            startDate = LocalDate.parse(dateMat.group(1).trim());
            endDate = LocalDate.parse(dateMat.group(2).trim());
        }

        // 4. Value ceiling (e.g. shall not exceed INR 20,00,000)
        Pattern capPat = Pattern.compile("INR\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE);
        Matcher capMat = capPat.matcher(rawText);
        if (capMat.find()) {
            String capStr = capMat.group(1).replace(",", "").trim();
            valueCap = new BigDecimal(capStr);
        }

        // 5. Lines table (e.g. 1 ITM-1036 Pneumatic cylinder 50x100 NOS 3,537.76)
        Pattern linePat = Pattern.compile("(\\d+)\\s+(ITM-\\d+)\\s+(.+?)\\s+([A-Z]{2,4})\\s+([0-9,]+(?:\\.\\d{1,2})?)");
        Matcher lineMat = linePat.matcher(rawText);
        while (lineMat.find()) {
            String itemCode = lineMat.group(2).trim();
            String desc = lineMat.group(3).trim();
            String uom = lineMat.group(4).trim();
            String rateStr = lineMat.group(5).replace(",", "").trim();
            lines.add(new ExtractedContractLine(itemCode, desc, uom, new BigDecimal(rateStr)));
        }

        boolean isValid = !contractId.isEmpty() && !supplierId.isEmpty() && startDate != null && endDate != null && valueCap.compareTo(BigDecimal.ZERO) > 0 && !lines.isEmpty();

        return new ExtractedContractDto(
                contractId,
                supplierId,
                supplierName,
                startDate,
                endDate,
                valueCap,
                BigDecimal.ZERO, // PDF contracts signed after register start at 0
                lines,
                rawText,
                isValid
        );
    }

    @Transactional
    public Contract activateContract(ExtractedContractDto dto) {
        Contract contract = new Contract(
                dto.contractId(),
                dto.supplierId(),
                dto.supplierName(),
                dto.startDate(),
                dto.endDate(),
                dto.valueCap(),
                dto.consumedValue() != null ? dto.consumedValue() : BigDecimal.ZERO,
                "PDF_UPLOAD",
                true
        );
        contractRepository.save(contract);

        for (ExtractedContractLine l : dto.lines()) {
            if (contractLineRepository.findByContractIdAndItemCode(dto.contractId(), l.itemCode()).isEmpty()) {
                contractLineRepository.save(new ContractLine(
                        dto.contractId(),
                        l.itemCode(),
                        l.description(),
                        l.uom(),
                        l.rate()
                ));
            }
        }

        log.info("Successfully activated PDF contract {} with {} lines", dto.contractId(), dto.lines().size());
        return contract;
    }
}
