package com.fastfile.service;

import com.fastfile.IntegrationTestSetup;
import com.fastfile.auth.JwtService;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import com.fastfile.service.deleteUser.DeleteUserService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.*;
import java.nio.file.NoSuchFileException;

import static com.fastfile.IntegrationTestSetup.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

// Integration test for {@link DeleteUserService}
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("resource")
public class DeleteUserServiceIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest").withReuse(true);

    @Autowired
    private FileService fileService;

    @Autowired
    private DeleteUserService deleteUserService;

    @Autowired
    private UserService userService;

    // CONFIG
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;

    @BeforeTransaction
    void beforeTransactionConfig() throws IOException {
        IntegrationTestSetup.beforeTransactionConfig(jdbcTemplate, userRepository, authService, jwtService);
    }

    @AfterEach
    void afterEachConfig() throws IOException {
        IntegrationTestSetup.afterEachConfig();
    }

    @AfterAll
    static void afterAllConfig() throws IOException {
        IntegrationTestSetup.afterAllConfig();
    }
    // END OF CONFIG

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
