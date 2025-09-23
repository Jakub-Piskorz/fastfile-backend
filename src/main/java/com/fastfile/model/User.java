package com.fastfile.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Data
@Entity
@Getter
@Setter
@Table(name="_user")
@RequiredArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @Column(nullable = false, unique = true)
    private String username;

    @NonNull
    @Column(nullable = false, unique = true)
    private String email;

    @NonNull
    @Column(nullable = false, name = "first_name")
    private String firstName;

    @NonNull
    @Column(nullable = false, name = "last_name")
    private String lastName;

    @NonNull
    @Column(nullable = false)
    private String password;

    @OneToMany(mappedBy = "sharedUser")
    private List<FileLinkShare> fileLinkShares;

    private String userType = "free";
    private Long usedStorage = 0L;
}
