package com.fastfile.service;

import com.fastfile.auth.JwtService;
import com.fastfile.dto.FileDTO;
import com.fastfile.dto.FilePathsDTO;
import com.fastfile.dto.UserLoginDTO;
import com.fastfile.model.User;
import com.fastfile.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.fastfile.service.FileSystemService.FILES_ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    @Autowired
    private UserService userService;

    @BeforeEach
    void setup() throws IOException {
        if (sqlInjected.compareAndSet(false, true)) {
            // Load schema.sql once
            Resource resource = new ClassPathResource("schema.sql");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                String sql = reader.lines().collect(Collectors.joining("\n"));
                jdbcTemplate.execute(sql);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load schema.sql", e);
            }

            // Create user folder
            Files.createDirectories(TEST_USER_DIR);
        }

        // SecurityContext for service tests
        UserLoginDTO loginDTO = new UserLoginDTO("testUser", "secretPassword");
        String jwtToken = restTemplate.postForObject("/auth/login", loginDTO, String.class);
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

        ResponseEntity<StreamingResponseBody> response = fileService.downloadFile("download.txt");
        assertThat(response.getStatusCode().toString()).isEqualTo("200 OK");
        assertThat(response.getBody()).isNotNull();

        response = fileService.downloadFile("not-existing-file.txt");
        assertThat(response.getStatusCode().toString()).isEqualTo("404 NOT_FOUND");
        assertThat(response.getBody()).isNull();
    }

    @Test
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
    void uploadWhenStorageExceeded() throws IOException {
        // Simulate upload exceeding user free storage limit
        long almostFreeLimit = userService.freeLimit-4;
        jdbcTemplate.execute("UPDATE _user SET used_storage =" + almostFreeLimit + " WHERE id = -1;");
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
        long almostPremiumLimit = userService.premiumLimit-4;
        jdbcTemplate.execute("UPDATE _user SET used_storage =" + almostPremiumLimit + " WHERE id = -1;");
        assertThat(userService.getMyUsedStorage()).isEqualTo(almostPremiumLimit);
        result = fileService.uploadFile(file, "/");

        // Assert that upload failed due to surpassing premium storage limit.
        assertThat(result).isFalse();
    }

    @Test
    @Transactional
    void removeAllFiles() throws IOException {
        String res = fileService.createMyPersonalDirectory("test");
        assertThat(res).isNull();
        MockMultipartFile file =
                new MockMultipartFile("file", "update.txt", "text/plain", "12345".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "update2.txt", "text/plain", "22345".getBytes());
        fileService.uploadFile(file, "/");
        fileService.uploadFile(file2, "/");
        fileService.uploadFile(file, "/test");
        fileService.uploadFile(file2, "/test");
        assertThat(fileService.filesInMyDirectory("/")).hasSize(3);
        assertThat(fileService.filesInMyDirectory("")).hasSize(3);
        assertThat(fileService.filesInMyDirectory("test")).hasSize(2);

        System.out.println(Files.exists(fileService.getMyUserPath()));
        boolean success = fileService.deleteMyPersonalDirectory();
        assertThat(success).isTrue();
        assertThat(fileService.filesInMyDirectory("test")).hasSize(0);
        assertThat(fileService.filesInMyDirectory("/")).hasSize(0);
    }
}
