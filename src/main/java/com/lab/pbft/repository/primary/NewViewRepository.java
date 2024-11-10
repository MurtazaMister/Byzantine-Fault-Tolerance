package com.lab.pbft.repository.primary;

import com.lab.pbft.model.primary.NewView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NewViewRepository extends JpaRepository<NewView, Integer> {
    @Query("SELECT nv FROM NewView nv LEFT JOIN FETCH nv.bundles ORDER BY nv.view ASC")
    List<NewView> findAllByOrderByViewAsc();
}
