package com.fastfile.repository;

import com.fastfile.model.FileLinkShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface FileLinkShareRepository extends JpaRepository<FileLinkShare, Long> {
    Set<FileLinkShare> findAllByFileLinkUuid(UUID fileLinkUuid);
    List<FileLinkShare> findAllBySharedUserEmail(String email);

    void deleteAllByFileLinkUuid(UUID fileLinkUuid);
}
