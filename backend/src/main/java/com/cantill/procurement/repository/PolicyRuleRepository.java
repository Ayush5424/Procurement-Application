package com.cantill.procurement.repository;

import com.cantill.procurement.domain.PolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {
    List<PolicyRule> findByPolicyVersionNumberOrderByPriorityAsc(String policyVersionNumber);
    void deleteByPolicyVersionNumber(String policyVersionNumber);
}
