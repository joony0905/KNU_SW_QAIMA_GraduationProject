package com.qaima.repository;

import com.qaima.domain.Sec13fImportFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Sec13fImportFileRepository extends JpaRepository<Sec13fImportFile, Long> {
    Optional<Sec13fImportFile> findBySourceFile(String sourceFile);
}
