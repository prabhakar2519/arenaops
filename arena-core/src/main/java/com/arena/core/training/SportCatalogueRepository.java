package com.arena.core.training;
public interface SportCatalogueRepository extends org.springframework.data.jpa.repository.JpaRepository<SportCatalogue,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SportCatalogue> {
 java.util.Optional<SportCatalogue> findByCodeAndActiveTrue(String code);
 java.util.List<SportCatalogue> findByActiveTrueOrderByLabel();
}
