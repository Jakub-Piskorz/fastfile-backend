package com.fastfile.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_link_uuid", referencedColumnName = "uuid", nullable = false)
    @JsonIgnore // This won't be sent in HTTP response
    private FileLink fileLink;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_user_email", referencedColumnName = "email", nullable = false)
    @JsonIgnore // This won't be sent in HTTP response
    private User sharedUser;
}