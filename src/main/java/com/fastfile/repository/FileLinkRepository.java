package com.fastfile.repository;

import com.fastfile.model.FileLink;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface FileLinkRepository extends JpaRepository<FileLink, UUID> {
    FileLink findByPath(@NonNull String path);

    List<FileLink> findAllByOwnerId(Long ownerId);

    boolean existsByPath(String path);

    Set<FileLink> findAllByPath(String string);
}
