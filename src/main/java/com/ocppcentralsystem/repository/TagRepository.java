package com.ocppcentralsystem.repository;

import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagStatus;
import com.ocppcentralsystem.model.TagType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TagRepository extends JpaRepository<Tag, String> {

    /**
     * Lists tags applying only the filters that were supplied. Passing {@code null}
     * for a filter ignores it; {@code search} matches the tag identifier or the
     * customer name, case-insensitively.
     */
    @Query("""
    SELECT t FROM Tag t
    WHERE (:status IS NULL OR t.status = :status)
      AND (:tagType IS NULL OR t.tagType = :tagType)
      AND (:search IS NULL
           OR LOWER(t.idTag) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(t.customerName) LIKE LOWER(CONCAT('%', :search, '%')))
    ORDER BY t.customerName ASC
    """)
    List<Tag> findAllFiltered(@Param("status") TagStatus status,
                              @Param("tagType") TagType tagType,
                              @Param("search") String search);

}
