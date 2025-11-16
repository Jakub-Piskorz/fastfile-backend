package com.fastfile.service;

import com.fastfile.auth.JwtService;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.fastfile.service.FileSystemService.FILES_ROOT;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:latest").withReuse(true);

    private static final String TEST_USERNAME = "testUser";
    private static final String TEST_PASSWORD = "secretPassword";

    // CONFIGURATION
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserService userService;
    @Autowired
    private AuthService authService;

    private static final Long TEST_USER_ID = -1L;
    private static final Path TEST_USER_DIR = Paths.get(FILES_ROOT, TEST_USER_ID.toString());
    @Autowired
    private UserRepository userRepository;

    @BeforeTransaction
    void setup() throws IOException {
        if (userRepository.findById(TEST_USER_ID).isEmpty()) {
            // Load schema.sql once
            Resource resource = new ClassPathResource("schema.sql");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                String sql = reader.lines().collect(Collectors.joining("\n"));
                jdbcTemplate.execute(sql);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load schema.sql", e);
            }

        }
        // Create user folder
        if (Files.notExists(TEST_USER_DIR)) {
            Files.createDirectories(TEST_USER_DIR);
        }

        // SecurityContext for service tests
        String jwtToken = authService.authenticate("testUser", "secretPassword");
        Claims claims = jwtService.extractClaims(jwtToken);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(TEST_USER_ID, null, List.of());
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void cleanTestDirectory() throws IOException {
        File testUserDir = new File(FILES_ROOT, TEST_USER_ID.toString());
        if (Files.exists(testUserDir.toPath())) {
            FileUtils.cleanDirectory(testUserDir);
        }
    }

    @AfterAll
    static void teardown() throws IOException {
        if (Files.exists(TEST_USER_DIR)) {
            Files.walk(TEST_USER_DIR)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        SecurityContextHolder.clearContext();
    }
    // END CONFIGURATION

    @Test
    public void connectionEstablished() {
        assertThat(postgres.isCreated()).isTrue();
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    @Transactional
    public void registerUser() {
        final User user = new User(
                "qbek2",
                "qbek@test.com",
                "Qbek",
                "Qbeczek",
                TEST_PASSWORD
        );
        final User registeredUser = userRepository.save(user);
        assertThat(registeredUser).isNotNull();
        assertThat(registeredUser.getUsername()).isEqualTo(user.getUsername());
        assertThat(registeredUser.getPassword()).isEqualTo(user.getPassword());
    }

    @Test
    @Transactional
    public void me() {
        User me = userService.getMe();
        assertThat(me).isNotNull();
        assertThat(me.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(me.getId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    public void findTestUser() {
        final Optional<User> result = userRepository.findByUsername(TEST_USERNAME);
        assertThat(result.orElseThrow().getLastName()).isEqualTo("testLastname");
    }

    @Test
    @Transactional
    void userTypes() {
        User user = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(user.getUserType()).isEqualTo("free");

        boolean result = userService.updateMyUserType("premium");
        assertThat(result).isTrue();
        user = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(user.getUserType()).isEqualTo("premium");

        result = userService.updateMyUserType("incorrect-type");
        assertThat(result).isFalse();
        user = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(user.getUserType()).isEqualTo("premium");
    }

    @Test
    @Transactional
    void usedStorage() {
        User me = userService.getMe();
        assertThat(me.getUserType()).isEqualTo("free");
        assertThat(me.getUsedStorage()).isEqualTo(0L);
        assertThat(userService.getMyUsedStorage()).isEqualTo(0L);
        assertThat(userService.getMyUserStorageLimit()).isEqualTo(userService.freeLimit);

        userService.updateMyUserType("premium");
        assertThat(userService.getMyUsedStorage()).isEqualTo(0L);
        me =  userService.getMe();
        assertThat(me.getUserType()).isEqualTo("premium");
        assertThat(userService.getMyUserStorageLimit()).isEqualTo(userService.premiumLimit);
    }
}