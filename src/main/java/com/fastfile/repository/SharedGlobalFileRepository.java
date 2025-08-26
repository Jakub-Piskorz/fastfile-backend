package com.fastfile.repository;

import com.fastfile.model.SharedGlobalFile;
import com.fastfile.model.User;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SharedGlobalFileRepository extends JpaRepository<SharedGlobalFile, UUID> {
    Optional<SharedGlobalFile> findByUuid(@NonNull UUID uuid);
    Optional<SharedGlobalFile> findByPath(@NonNull String path);
    Optional<SharedGlobalFile> findByOwner_Id(Long ownerId);

    @Query("SELECT sgf.owner FROM SharedGlobalFile sgf WHERE sgf.uuid = :uuid")
    Optional<User> findOwnerByUuid(UUID uuid);

    @Query("SELECT sgf.owner FROM SharedGlobalFile sgf WHERE sgf.path = :path")
    Optional<User> findOwnerByPath(String path);

    @Query("SELECT sgf FROM SharedGlobalFile sgf WHERE sgf.owner.id = :userId")
    Set<SharedGlobalFile> findFilesSharedBy(Long userId);

    boolean existsByUuid(@NonNull UUID uuid);
}
