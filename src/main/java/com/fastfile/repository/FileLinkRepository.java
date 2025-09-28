package com.fastfile.repository;

import com.fastfile.model.FileLink;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileLinkRepository extends JpaRepository<FileLink, UUID> {
    Optional<FileLink> findByPath(@NonNull String path);
    List<FileLink> findAllByPath(@NonNull String path);

    List<FileLink> findAllByOwnerId(Long ownerId);
}
