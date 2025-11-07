package com.fastfile.service;

import com.fastfile.auth.JwtService;
import com.fastfile.dto.FileDTO;
import com.fastfile.dto.UserLoginDTO;
import com.fastfile.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.fastfile.service.FileSystemService.FILES_ROOT;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // no web server needed
public class FileServiceIT {
    @Autowired
    TestRestTemplate restTemplate;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest").withReuse(true);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileService fileService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private static final Long TEST_USER_ID = -1L;
    private static final Path TEST_USER_DIR = Paths.get(FILES_ROOT, TEST_USER_ID.toString());
    private static final AtomicBoolean sqlInjected = new AtomicBoolean(false);

    @BeforeEach
    void setup() throws IOException {
        if (sqlInjected.compareAndSet(false, true)) {
            // Load your schema.sql once
            Resource resource = new ClassPathResource("schema.sql");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                String sql = reader.lines().collect(Collectors.joining("\n"));
                jdbcTemplate.execute(sql);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load schema.sql", e);
            }
        }

        // SecurityContext for service tests
        UserLoginDTO loginDTO = new UserLoginDTO("testUser", "secretPassword");
        String jwtToken = restTemplate.postForObject("/auth/login", loginDTO, String.class);
        Claims claims = jwtService.extractClaims(jwtToken);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(TEST_USER_ID, null, List.of());
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Create user folder
        Files.createDirectories(TEST_USER_DIR);

        System.out.println("Successfully set up authentication");
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

    @Test
    void connectionEstablished() {
        assertThat(postgres.isCreated()).isTrue();
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void userExists() {
        assertThat(userRepository.existsById(TEST_USER_ID)).isTrue();
    }

    @Test
    void uploadFileTest() throws IOException {
        String content = "Hello FastFile!";
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );
        fileService.uploadFile(multipartFile, "/");
        assertThat(Files.exists(Paths.get(TEST_USER_DIR + "/test.txt"))).isTrue();
    }

    @Test
    void deleteFileTest() throws IOException {
        Path tempFile = Files.createTempFile(TEST_USER_DIR, "delete", ".txt");
        fileService.delete(tempFile.getFileName().toString());
        assertThat(Files.exists(tempFile)).isFalse();
    }


    @Test
    void deleteRecursivelyDirectory() throws IOException {
        fileService.createMyPersonalDirectory("toDelete/subDir");
        MockMultipartFile file = new MockMultipartFile("file", "f.txt", "text/plain", "c".getBytes());
        fileService.uploadFile(file, "toDelete/subDir");

        boolean deleted = fileService.deleteRecursively("toDelete");
        assertThat(deleted).isTrue();
        assertThat(Files.exists(TEST_USER_DIR.resolve("toDelete"))).isFalse();
    }

    @Test
    void downloadFileExists() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "download.txt", "text/plain", "data".getBytes());
        fileService.uploadFile(file, "/");

        var response = fileService.downloadFile("download.txt");
        assertThat(response.getStatusCode().toString()).isEqualTo("200 OK");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void searchFilesByName() throws IOException {
        MockMultipartFile fileA = new MockMultipartFile("file", "a_test.txt", "text/plain", "data".getBytes());
        MockMultipartFile fileB = new MockMultipartFile("file", "b_test.txt", "text/plain", "data".getBytes());

        fileService.uploadFile(fileA, "/");
        fileService.uploadFile(fileB, "/");

        List<FileDTO> results = fileService.searchFiles("a_", null);
        assertThat(results)
                .extracting("metadata").isNotNull()
                .extracting("name").containsExactly("a_test.txt");
    }
}
