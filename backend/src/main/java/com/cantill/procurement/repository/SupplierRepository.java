package com.cantill.procurement.repository;

import com.cantill.procurement.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, String> {
    List<Supplier> findByStatusIgnoreCase(String status);
}
