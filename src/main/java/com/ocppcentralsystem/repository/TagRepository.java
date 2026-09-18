package com.ocppcentralsystem.repository;

import com.ocppcentralsystem.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, String>, JpaSpecificationExecutor<Tag> {

    Optional<Tag> findByTenantAndIdTag(String tenant, String idTag);

    boolean existsByTenantAndIdTag(String tenant, String idTag);

}
