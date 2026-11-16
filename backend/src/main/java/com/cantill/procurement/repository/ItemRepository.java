package com.cantill.procurement.repository;

import com.cantill.procurement.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, String> {
    List<Item> findByCategoryIgnoreCase(String category);
    List<Item> findByDescriptionContainingIgnoreCase(String description);
}
