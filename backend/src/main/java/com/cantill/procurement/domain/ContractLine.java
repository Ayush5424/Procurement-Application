package com.cantill.procurement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "contract_lines", indexes = {
    @Index(name = "idx_contract_lines_item", columnList = "item_code"),
    @Index(name = "idx_contract_lines_contract", columnList = "contract_id")
})
public class ContractLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", length = 50, nullable = false)
    private String contractId;

    @Column(name = "item_code", length = 50, nullable = false)
    private String itemCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "uom", length = 20, nullable = false)
    private String uom;

    @Column(name = "rate", precision = 15, scale = 2, nullable = false)
    private BigDecimal rate;

    public ContractLine() {}

    public ContractLine(String contractId, String itemCode, String description, String uom, BigDecimal rate) {
        this.contractId = contractId;
        this.itemCode = itemCode;
        this.description = description;
        this.uom = uom;
        this.rate = rate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
}
