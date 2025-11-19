package com.fastfile.service;

import com.fastfile.IntegrationTestSetup;
import com.fastfile.auth.JwtService;
import com.fastfile.dto.FileDTO;
import com.fastfile.model.FileLink;
import com.fastfile.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static com.fastfile.IntegrationTestSetup.TEST_USER_DIR;
import static org.assertj.core.api.Assertions.assertThat;

// Integration test for {@link FileService}
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FileLinkServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @Autowired
    private FileService fileService;
    @Autowired
    private FileLinkService fileLinkService;

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

    private void uploadSomeFiles() throws IOException {
        Function<String, MockMultipartFile> mockFile = (fileName) -> new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                "Hello FastFile!".getBytes()
        );
        var file1 = mockFile.apply("file1.txt");
        var file2 = mockFile.apply("file2.txt");
        var file3 = mockFile.apply("file3.txt");

        // Upload them
        fileService.uploadFile(file1, "/");
        fileService.uploadFile(file2, "/");
        fileService.createMyPersonalDirectory("nested");
        fileService.uploadFile(file3, "/nested");
    }

    @Transactional
    @Test
    void uploadFiles() throws IOException {
        uploadSomeFiles();
        List<FileDTO> files = fileService.filesInMyDirectory("");
        assertThat(files).hasSize(3);
        files = fileService.filesInMyDirectory("nested");
        assertThat(files).hasSize(1);
    }

    @Transactional
    @Test
    void createPublicLink() throws IOException {
        uploadSomeFiles();
        FileLink fileLink = fileLinkService.createPublicFileLink(TEST_USER_DIR + "/file1.txt");
        assertThat(fileLink).isNotNull();
        assertThat(fileLink.getPath()).isEqualTo(TEST_USER_DIR + "/file1.txt");
    }

    @Transactional
    @Test
    void failCreatingPublicLink() throws IOException {
        uploadSomeFiles();
        FileLink fileLink = fileLinkService.createPublicFileLink(TEST_USER_DIR + "/fake-file.txt");
        assertThat(fileLink).isNull();
    }

    @Transactional
    @Test
    void createPublicLinkInNestedFolder() throws IOException {
        uploadSomeFiles();
        FileLink fileLink = fileLinkService.createPublicFileLink(TEST_USER_DIR + "/nested/file3.txt");
        assertThat(fileLink).isNotNull();
        assertThat(fileLink.getPath()).isEqualTo(TEST_USER_DIR + "/nested/file3.txt");
    }
}
