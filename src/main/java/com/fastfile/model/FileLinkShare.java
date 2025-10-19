package com.fastfile.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Table(name = "file_link_share")
@Getter
@Setter
@RequiredArgsConstructor
@NoArgsConstructor
public class FileLinkShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @Column(name = "file_link_uuid", nullable = false)
    private UUID fileLinkUuid;

    @NonNull
    @Column(name = "shared_user_email", nullable = false)
    private String sharedUserEmail;
}