package com.cantill.procurement.repository;

import com.cantill.procurement.domain.Plant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlantRepository extends JpaRepository<Plant, String> {
}
