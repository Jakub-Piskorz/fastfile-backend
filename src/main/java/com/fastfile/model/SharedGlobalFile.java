package com.fastfile.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Table(name = "shared_global_file")
@Getter
@Setter
@RequiredArgsConstructor
@NoArgsConstructor
public class SharedGlobalFile {
    @NonNull
    @Id
    @Column(nullable = false, unique = true)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @NonNull
    @JoinColumn(name = "owner_id", referencedColumnName = "id", nullable = false)
    @JsonIgnore
    private User owner;

    @NonNull
    @Column(nullable = false, unique = true)
    private String path;
}