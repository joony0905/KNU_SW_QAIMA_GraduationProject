package com.qaima.repository;

import com.qaima.domain.Sec13fFiling;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Sec13fFilingRepository extends JpaRepository<Sec13fFiling, Long> {
    Optional<Sec13fFiling> findByAccessionNumber(String accessionNumber);

    List<Sec13fFiling> findByAccessionNumberIn(Collection<String> accessionNumbers);
}
