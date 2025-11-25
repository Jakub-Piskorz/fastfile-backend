package com.fastfile.service;

import com.fastfile.IntegrationTestSetup;
import com.fastfile.auth.JwtService;
import com.fastfile.config.FilesConfig;
import com.fastfile.dto.FileDTO;
import com.fastfile.dto.FilePathsDTO;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.fastfile.IntegrationTestSetup.TEST_USER_DIR;
import static com.fastfile.IntegrationTestSetup.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

// Integration test for {@link FileService}
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FileServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @Autowired
    private FileService fileService;

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
        Path testUserDir = Paths.get(FilesConfig.FILES_ROOT, TEST_USER_ID.toString());
        IntegrationTestSetup.afterAllConfig(testUserDir);
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
    @Transactional
    void deleteFileTest() throws IOException {
        assertThat(Files.exists(TEST_USER_DIR)).isTrue();
        Path tempFile = Files.createTempFile(TEST_USER_DIR, "delete", ".txt");
        fileService.delete(tempFile.getFileName().toString());
        assertThat(Files.exists(tempFile)).isFalse();
    }


    @Test
    @Transactional
    void deleteRecursivelyDirectory() throws IOException {
        fileService.createMyPersonalDirectory("toDelete/subDir");
        MockMultipartFile file = new MockMultipartFile("file", "f.txt", "text/plain", "c".getBytes());
        fileService.uploadFile(file, "toDelete/subDir");

        boolean deleted = fileService.deleteRecursively("toDelete");
        assertThat(deleted).isTrue();
        assertThat(Files.exists(TEST_USER_DIR.resolve("toDelete"))).isFalse();
    }

    @Test
    @Transactional
    void downloadFileExists() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "download.txt", "text/plain", "data".getBytes());
        fileService.uploadFile(file, "/");

        ResponseEntity<StreamingResponseBody> response = fileService.downloadFile("download.txt");
        assertThat(response.getStatusCode().toString()).isEqualTo("200 OK");
        assertThat(response.getBody()).isNotNull();

        response = fileService.downloadFile("not-existing-file.txt");
        assertThat(response.getStatusCode().toString()).isEqualTo("404 NOT_FOUND");
        assertThat(response.getBody()).isNull();
    }

    @Test
    @Transactional
    void searchFilesByName() throws IOException {
        MockMultipartFile fileA = new MockMultipartFile("file", "a_test.txt", "text/plain", "data".getBytes());
        MockMultipartFile fileB = new MockMultipartFile("file", "b_test.txt", "text/plain", "data".getBytes());

        fileService.uploadFile(fileA, "/");
        fileService.uploadFile(fileB, "/");

        List<FileDTO> results = fileService.searchFiles("a_", "/");
        assertThat(results)
                .extracting("metadata").isNotNull()
                .extracting("name").containsExactly("a_test.txt");

        results = fileService.searchFiles("test", "/");
        assertThat(results).hasSize(2);

        results = fileService.searchFiles("test");
        assertThat(results).hasSize(2);

        results = fileService.searchFiles("not-existing-file", "/");
        assertThat(results).isEmpty();

        assertThrows(IOException.class, () -> fileService.searchFiles("test", "/not-existing-directory"));
    }

    @Test
    @Transactional
    void listFilesInDirectory() throws IOException {
        MockMultipartFile file1 = new MockMultipartFile("file", "file1.txt", "text/plain", "a".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "file2.txt", "text/plain", "b".getBytes());
        MockMultipartFile file3 = new MockMultipartFile("file", "file3.txt", "text/plain", "c".getBytes());
        MockMultipartFile file4 = new MockMultipartFile("file", "file4.txt", "text/plain", "d".getBytes());

        fileService.uploadFile(file1, "/");
        fileService.uploadFile(file2, "/");
        fileService.createMyPersonalDirectory("nested");
        fileService.uploadFile(file3, "/nested");
        fileService.uploadFile(file4, "/nested");

        var files = fileService.filesInMyDirectory("", 1);
        assertThat(files).hasSize(3);
        assertThat(files)
                .extracting("metadata").isNotNull()
                .extracting("name").containsExactly("nested", "file1.txt", "file2.txt");

        files = fileService.filesInMyDirectory("nested", 1);
        assertThat(files).hasSize(2);
        assertThat(files)
                .extracting("metadata").isNotNull()
                .extracting("name").containsExactly("file3.txt", "file4.txt");

        files = fileService.filesInMyDirectory("", 2);
        assertThat(files).hasSize(5);
        assertThat(files)
                .extracting("metadata").isNotNull()
                .extracting("name").containsExactly("nested", "file1.txt", "file2.txt", "file3.txt", "file4.txt");

        assertThrows(IOException.class, () -> fileService.filesInMyDirectory("not-existing-dir", 2));
    }

    @Test
    @Transactional
    void deleteNonExistingFile() {
        assertThrows(IOException.class, () -> fileService.delete("nonexistent.txt"));
    }

    @Test
    @Transactional
    void updateUserStorageAfterUpload() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "update.txt", "text/plain", "12345".getBytes());
        fileService.uploadFile(file, "/");

        fileService.updateUserStorage(TEST_USER_ID);
        var user = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(user.getUsedStorage()).isEqualTo(5); // length of "12345"
    }

    @Test
    @Transactional
    void downloadMultiple() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "update.txt", "text/plain", "12345".getBytes());
        fileService.uploadFile(file, "/");

        MockMultipartFile file2 = new MockMultipartFile("file", "update2.txt", "text/plain", "22345".getBytes());
        fileService.uploadFile(file2, "/");

        List<FileDTO> files = fileService.filesInMyDirectory("/");
        assertThat(files).hasSize(2);

        ResponseEntity<StreamingResponseBody> response =
                fileService.downloadMultiple(new FilePathsDTO(Arrays.asList("update.txt", "update2.txt")));
        assertThat(response.getBody()).isNotNull();

        // Check headers
        String contentType = Objects.requireNonNull(response.getHeaders().getContentType()).toString();
        assertThat(contentType).isIn("application/zip", "application/x-zip-compressed");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("download.zip");

        // Check ZIP structure
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);

        try (ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
             ZipInputStream zipIn = new ZipInputStream(in)) {

            Set<String> entryNames = new HashSet<>();
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }

            assertThat(entryNames).containsExactlyInAnyOrder("update.txt", "update2.txt");
        }
    }

    @Test
    @Transactional
    void uploadWhenStorageExceeded() throws IOException {
        long almostFreeLimit = userService.freeLimit - 4;
        long almostPremiumLimit = userService.premiumLimit - 4;

        User me = userService.getMe();
        assertThat(me.getUsedStorage()).isEqualTo(0L);

        // Simulate upload exceeding user free storage limit
        me = userService.getMe();
        me.setUsedStorage(almostFreeLimit);
        userRepository.save(me);
        assertThat(userService.getMyUsedStorage()).isEqualTo(almostFreeLimit);
        MockMultipartFile file = new MockMultipartFile("file", "update.txt", "text/plain", "12345".getBytes());
        boolean result = fileService.uploadFile(file, "/");

        // Assert that upload failed due to surpassing free storage limit.
        assertThat(result).isFalse();

        // Check if user became premium
        result = userService.updateMyUserType("premium");
        User user = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(result).isTrue();
        assertThat(user.getUserType()).isEqualTo("premium");

        // Assert that upload succeeded after upgrating user storage to premium.
        result = fileService.uploadFile(file, "/");
        assertThat(result).isTrue();

        // Remove file
        fileService.delete("update.txt");

        // Check if user used storage has been updated after file removal
        assertThat(userService.getMyUsedStorage()).isEqualTo(0L);

        // Simulate upload exceeding user premium storage limit
        me = userService.getMe();
        me.setUsedStorage(almostPremiumLimit);
        userRepository.save(user);
        assertThat(userService.getMyUsedStorage()).isEqualTo(almostPremiumLimit);
        result = fileService.uploadFile(file, "/");

        // Assert that upload failed due to surpassing premium storage limit.
        assertThat(result).isFalse();
    }
}
