package com.fastfile.service;

import com.fastfile.auth.JwtService;
import com.fastfile.config.GlobalVariables;
import com.fastfile.dto.UserLoginDTO;
import com.fastfile.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

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
    private JwtService jwtService;

    private static final Long TEST_USER_ID = -1L;
    private static final Path TEST_USER_DIR = Paths.get(FILES_ROOT, TEST_USER_ID.toString());

    @BeforeEach
    void setup() throws IOException {
        // SecurityContext for service tests
        UserLoginDTO loginDTO = new UserLoginDTO("testUser", "secretPassword");
        String jwtToken = restTemplate.postForObject("/auth/login", loginDTO, String.class);
        Claims claims = jwtService.extractClaims(jwtToken);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(TEST_USER_ID, null, List.of());
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Create user folder
        Files.createDirectories(TEST_USER_DIR);
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
    @Sql(scripts = "/schema.sql")
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
}
