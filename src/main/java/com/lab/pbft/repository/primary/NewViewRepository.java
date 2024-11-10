package com.lab.pbft.repository.primary;

import com.lab.pbft.model.primary.NewView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewViewRepository extends JpaRepository<NewView, Integer> {
    List<NewView> findAllByOrderByViewAsc();
}
