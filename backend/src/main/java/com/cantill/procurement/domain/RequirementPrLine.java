package com.cantill.procurement.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "requirement_pr_lines", indexes = {
    @Index(name = "idx_req_pr_lines", columnList = "group_id")
})
public class RequirementPrLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", length = 50, nullable = false)
    private String groupId;

    @Column(name = "pr_no", length = 50, nullable = false)
    private String prNo;

    @Column(name = "pr_line", nullable = false)
    private Integer prLine;

    public RequirementPrLine() {}

    public RequirementPrLine(String groupId, String prNo, Integer prLine) {
        this.groupId = groupId;
        this.prNo = prNo;
        this.prLine = prLine;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getPrNo() { return prNo; }
    public void setPrNo(String prNo) { this.prNo = prNo; }

    public Integer getPrLine() { return prLine; }
    public void setPrLine(Integer prLine) { this.prLine = prLine; }
}
