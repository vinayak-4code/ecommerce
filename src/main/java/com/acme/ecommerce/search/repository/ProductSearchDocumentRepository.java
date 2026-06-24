package com.acme.ecommerce.search.repository;

import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.search.entity.ProductSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductSearchDocumentRepository extends JpaRepository<ProductSearchDocument, UUID> {
    @Query("""
            select d from ProductSearchDocument d
            where (:query is null or lower(d.name) like lower(concat('%', :query, '%')) or lower(d.description) like lower(concat('%', :query, '%')))
              and (:categoryId is null or d.categoryId = :categoryId)
              and (:status is null or d.status = :status)
            """)
    Page<ProductSearchDocument> search(
            @Param("query") String query,
            @Param("categoryId") UUID categoryId,
            @Param("status") ProductStatus status,
            Pageable pageable
    );
}
