package com.fastfile.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "file_link")
@Getter
@Setter
@RequiredArgsConstructor
@NoArgsConstructor

public class FileLink {
    @NonNull
    @Id
    @Column(nullable = false, unique = true, columnDefinition = "UUID")
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @NonNull
    @JoinColumn(name = "owner_id", referencedColumnName = "id", nullable = false)
    @JsonIgnore
    private User owner;

    @NonNull
    @Column(nullable = false, unique = true)
    private String path;

    @NonNull
    @Column(nullable = false)
    private Boolean isPublic;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_link_uuid", referencedColumnName = "uuid")
    private List<FileLinkShare> fileLinkShares;
}