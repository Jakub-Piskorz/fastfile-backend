package com.fastfile.repository;

import com.fastfile.model.FileLink;
import com.fastfile.model.User;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface FileLinkRepository extends JpaRepository<FileLink, UUID> {
    Optional<FileLink> findByUuid(@NonNull UUID uuid);
    Optional<FileLink> findByPath(@NonNull String path);
    Optional<FileLink> findByOwner_Id(Long ownerId);

    @Query("SELECT link.owner FROM FileLink link WHERE link.uuid = :uuid")
    Optional<User> findOwnerByUuid(UUID uuid);

    @Query("SELECT link.owner FROM FileLink link WHERE link.path = :path")
    Optional<User> findOwnerByPath(String path);

    @Query("SELECT link FROM FileLink link WHERE link.owner.id = :userId")
    Set<FileLink> findFilesSharedBy(Long userId);

    boolean existsByUuid(@NonNull UUID uuid);

    boolean existsByPath(@NonNull String path);
}
