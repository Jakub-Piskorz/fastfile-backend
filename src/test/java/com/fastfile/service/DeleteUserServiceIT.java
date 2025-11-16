package com.fastfile.service;

import com.fastfile.auth.JwtService;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import com.fastfile.service.deleteUser.DeleteUserService;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.fastfile.service.FileSystemService.FILES_ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DeleteUserServiceIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest").withReuse(true);

    @Autowired
    private FileService fileService;

    @Autowired
    private DeleteUserService deleteUserService;

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
    void connectionEstablished() {
        assertThat(postgres.isCreated()).isTrue();
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void userExists() {
        User me = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(me).isNotNull();
        assertThat(me.getUsedStorage()).isZero();
    }

    @Test
    @Transactional
    void deleteUser() throws IOException {

        String res = fileService.createMyPersonalDirectory("test");
        assertThat(res).isNull();
        MockMultipartFile file =
                new MockMultipartFile("file", "update.txt", "text/plain", "12345".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "update2.txt", "text/plain", "22345".getBytes());
        MockMultipartFile file3 = new MockMultipartFile("file", "update3.txt", "text/plain", "33345".getBytes());
        MockMultipartFile file4 = new MockMultipartFile("file", "update4.txt", "text/plain", "33345".getBytes());

        boolean result = fileService.uploadFile(file, "/");
        assertThat(result).isTrue();
        result = fileService.uploadFile(file2, "/");
        assertThat(result).isTrue();
        result = fileService.uploadFile(file3, "/test");
        assertThat(result).isTrue();
        result = fileService.uploadFile(file4, "/test");
        assertThat(result).isTrue();
        assertThat(fileService.filesInMyDirectory("/")).hasSize(3);
        assertThat(fileService.filesInMyDirectory("")).hasSize(3);
        assertThat(fileService.filesInMyDirectory("test")).hasSize(2);

        User me = userService.getMe();
        boolean success = deleteUserService.deleteUser(me);
        assertThat(success).isTrue();
        assertThrows(NoSuchFileException.class, () -> fileService.filesInMyDirectory(""));
        assertThrows(NoSuchFileException.class, () -> fileService.filesInMyDirectory("/"));
        assertThrows(NoSuchFileException.class, () -> fileService.filesInMyDirectory("test"));

        // Check if user is deleted
        me = userService.getMe();
        assertThat(me).isNull();
    }

    @Test
    @Transactional
    void deleteMe() throws IOException {

        String res = fileService.createMyPersonalDirectory("test");
        assertThat(res).isNull();
        MockMultipartFile file =
                new MockMultipartFile("file", "update.txt", "text/plain", "12345".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "update2.txt", "text/plain", "22345".getBytes());
        MockMultipartFile file3 = new MockMultipartFile("file", "update3.txt", "text/plain", "33345".getBytes());
        MockMultipartFile file4 = new MockMultipartFile("file", "update4.txt", "text/plain", "33345".getBytes());

        boolean result = fileService.uploadFile(file, "/");
        assertThat(result).isTrue();
        result = fileService.uploadFile(file2, "/");
        assertThat(result).isTrue();
        result = fileService.uploadFile(file3, "/test");
        assertThat(result).isTrue();
        result = fileService.uploadFile(file4, "/test");
        assertThat(result).isTrue();
        assertThat(fileService.filesInMyDirectory("/")).hasSize(3);
        assertThat(fileService.filesInMyDirectory("")).hasSize(3);
        assertThat(fileService.filesInMyDirectory("test")).hasSize(2);

        boolean success = deleteUserService.deleteMe();
        assertThat(success).isTrue();
        assertThrows(NoSuchFileException.class, () -> fileService.filesInMyDirectory(""));
        assertThrows(NoSuchFileException.class, () -> fileService.filesInMyDirectory("/"));
        assertThrows(NoSuchFileException.class, () -> fileService.filesInMyDirectory("test"));

        // Check if user is deleted
        User me = userService.getMe();
        assertThat(me).isNull();
    }
}
