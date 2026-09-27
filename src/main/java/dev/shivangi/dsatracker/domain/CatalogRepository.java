package dev.shivangi.dsatracker.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogRepository extends JpaRepository<CatalogProblem, Integer> {

    /** All 150, in NeetCode roadmap order (ids were assigned in that order). */
    List<CatalogProblem> findAllByOrderByIdAsc();
}
