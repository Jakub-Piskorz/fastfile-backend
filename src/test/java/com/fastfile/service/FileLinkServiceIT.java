package com.fastfile.service;

import com.fastfile.IntegrationTestSetup;
import com.fastfile.auth.JwtService;
import com.fastfile.config.FilesConfig;
import com.fastfile.dto.FileDTO;
import com.fastfile.model.FileLink;
import com.fastfile.model.FileLinkShare;
import com.fastfile.model.User;
import com.fastfile.repository.FileLinkRepository;
import com.fastfile.repository.FileLinkShareRepository;
import com.fastfile.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fastfile.IntegrationTestSetup.TEST_USER_DIR;
import static com.fastfile.IntegrationTestSetup.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

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

    @Autowired
    private EntityManager em;

    // CONFIG
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FileLinkRepository fileLinkRepository;
    @Autowired
    private FileLinkShareRepository fileLinkShareRepository;

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

    @Transactional
    @Test
    void lookupPublicLink() throws IOException {
        uploadSomeFiles();
        FileLink fileLink = fileLinkService.createPublicFileLink(TEST_USER_DIR + "/nested/file3.txt");
        FileDTO file = fileLinkService.lookupFile(fileLink.getUuid());
        assertThat(file).isNotNull();
        assertThat(file.fileLink()).isNotNull();
        assertThat(file.fileLink().getPath()).isEqualTo(TEST_USER_DIR + "/nested/file3.txt");
        assertThat(file.fileLink().getIsPublic()).isTrue();
        assertThat(file.fileLink().getFileLinkShares()).isNull();
    }

    @Transactional
    @Test
    void createPrivateLink() throws IOException {
        uploadSomeFiles();
        List<String> emails = List.of("example@example.com", "example2@example.com");
        FileLink fileLink = fileLinkService.createPrivateFileLink(TEST_USER_DIR + "/nested/file3.txt", emails);
        assertThat(fileLink).isNotNull();

        List<FileLinkShare> shares = fileLink.getFileLinkShares();
        assertThat(shares).isNotNull();
        assertThat(shares).hasSize(2);

        fileLinkRepository.flush();
        em.clear();

        FileLink updatedFileLink = fileLinkRepository.findById(fileLink.getUuid()).orElse(null);
        assertThat(updatedFileLink).isNotNull();
        assertThat(updatedFileLink.getFileLinkShares()).hasSize(2);
    }

    @Transactional
    @Test
    void updatePrivateLink() throws IOException {
        uploadSomeFiles();
        List<String> emails = List.of("example@example.com", "example2@example.com");
        FileLink fileLink = fileLinkService.createPrivateFileLink(TEST_USER_DIR + "/nested/file3.txt", emails);

        List<String> updatedEmails = List.of("different@different.com", "different2@different.com", "different3@different.com");
        FileLink returnedFileLink = fileLinkService.updatePrivateLinkEmails(fileLink.getUuid(), updatedEmails);

        // Check updated e-mails on returned link.
        assertThat(returnedFileLink).isNotNull();
        assertThat(returnedFileLink.getFileLinkShares()).isNotNull();
        assertThat(returnedFileLink.getFileLinkShares()).hasSize(3);
        List<String> emailsFromLink = returnedFileLink
                .getFileLinkShares()
                .stream()
                .map(FileLinkShare::getSharedUserEmail)
                .collect(Collectors.toList());
        assertThat(emailsFromLink).isEqualTo(updatedEmails);

        // Check again, but this time, load link from DB.
        FileLink updatedFileLink = fileLinkRepository.findById(fileLink.getUuid()).orElse(null);
        assertThat(updatedFileLink).isNotNull();
        assertThat(updatedFileLink.getFileLinkShares()).isNotNull();
        assertThat(updatedFileLink.getFileLinkShares()).hasSize(3);
        emailsFromLink = updatedFileLink
                .getFileLinkShares()
                .stream()
                .map(FileLinkShare::getSharedUserEmail)
                .collect(Collectors.toList());
        assertThat(emailsFromLink).isEqualTo(updatedEmails);
    }

    @Transactional
    @Test
    void removeFileLink() throws IOException {
        uploadSomeFiles();

        FileLink fileLink = fileLinkService.createPublicFileLink(TEST_USER_DIR + "/file1.txt");
        assertThat(fileLink).isNotNull();
        assertThat(fileLink.getPath()).isEqualTo(TEST_USER_DIR + "/file1.txt");

        FileDTO file = fileLinkService.lookupFile(fileLink.getUuid());
        assertThat(file).isNotNull();
        assertThat(file.fileLink().getIsPublic()).isTrue();
        assertThat(file.fileLink().getFileLinkShares()).isNull();

        boolean deleted = fileLinkService.removeFileLink(fileLink.getUuid());
        assertThat(deleted).isTrue();

        assertThrows(NoSuchElementException.class, () -> fileLinkService.lookupFile(fileLink.getUuid()));
    }

    @Transactional
    @Test
    void deletePrivateLink() throws IOException {
        uploadSomeFiles();

        List<String> emails = List.of("example@example.com", "example2@example.com");
        FileLink fileLink = fileLinkService.createPrivateFileLink(TEST_USER_DIR + "/file1.txt", emails);
        assertThat(fileLink).isNotNull();
        assertThat(fileLink.getPath()).isEqualTo(TEST_USER_DIR + "/file1.txt");
        assertThat(fileLink.getFileLinkShares()).isNotNull();

        FileDTO file = fileLinkService.lookupFile(fileLink.getUuid());
        assertThat(file).isNotNull();
        assertThat(file.fileLink().getIsPublic()).isFalse();
        assertThat(file.fileLink().getFileLinkShares()).isNotNull();
        assertThat(file.fileLink().getFileLinkShares()).hasSize(2);
        List<String> emailsFromLink = file.fileLink().getFileLinkShares().stream().map(FileLinkShare::getSharedUserEmail).toList();
        Assertions.assertEquals(emailsFromLink, emails);


        boolean deleted = fileLinkService.removeFileLink(fileLink.getUuid());
        assertThat(deleted).isTrue();

        assertThrows(NoSuchElementException.class, () -> fileLinkService.lookupFile(fileLink.getUuid()));
    }

    @Transactional
    @Test
    void throwOnModifyingSomeoneElseLink() {

        // Add new user
        User secondUser = new User("testUser2", "example2@example.com", "firstName", "lastName", "password");
        userRepository.saveAndFlush(secondUser);
        User secondUserFromRepo = userRepository.findById(secondUser.getId()).orElse(null);
        assertThat(secondUserFromRepo).isNotNull();
        assertThat(secondUserFromRepo.getUsername()).isEqualTo("testUser2");

        // Create new private file link of second user
        Path secondUserDir = Paths.get(FilesConfig.FILES_ROOT, secondUserFromRepo.getId().toString());
        UUID uuid = UUID.randomUUID();
        FileLink fileLink = new FileLink(uuid, secondUser, secondUserDir + "/fake-file.txt", false);

        fileLinkRepository.saveAndFlush(fileLink);

        FileLink fileLinkFromRepo = fileLinkRepository.findById(fileLink.getUuid()).orElse(null);
        assertThat(fileLinkFromRepo).isNotNull();
        assertThat(fileLinkFromRepo.getOwner().getId()).isEqualTo(secondUser.getId());

        // Add share to that private link
        FileLinkShare share = new FileLinkShare(uuid, "example@example.com");
        fileLinkShareRepository.saveAndFlush(share);
        Set<FileLinkShare> sharesFromRepo = fileLinkShareRepository.findAllByFileLinkUuid(uuid);
        assertThat(sharesFromRepo).isNotNull();
        assertThat(sharesFromRepo).hasSize(1);

        FileLinkShare shareFromRepo = null;
        for (FileLinkShare shr : sharesFromRepo) {
            shareFromRepo = shr;
        }

        assertThat(shareFromRepo).isNotNull();
        assertThat(shareFromRepo.getFileLinkUuid()).isEqualTo(uuid);

        // Try to update/remove link of second user. Throw, because I'm not logged as them.
        assertThrows(AccessDeniedException.class, () -> fileLinkService.removeFileLink(fileLinkFromRepo.getUuid()));
        assertThrows(AccessDeniedException.class, () -> fileLinkService.updatePrivateLinkEmails(
                fileLinkFromRepo.getUuid(), List.of("different@mail.com", "different2@mail.com")));
    }

    @Test
    @Transactional
    void downloadLink() throws IOException {
        uploadSomeFiles();

        // Create public link
        FileLink fileLink = fileLinkService.createPublicFileLink(TEST_USER_DIR + "/file1.txt");
        assertThat(fileLink).isNotNull();
        assertThat(fileLink.getPath()).isEqualTo(TEST_USER_DIR + "/file1.txt");

        FileDTO file = fileLinkService.lookupFile(fileLink.getUuid());
        assertThat(file).isNotNull();
        assertThat(file.fileLink().getIsPublic()).isTrue();
        assertThat(file.fileLink().getFileLinkShares()).isNull();

        // Download file from link
        ResponseEntity<StreamingResponseBody> response = fileLinkService.downloadFileFromLink(file.fileLink().getUuid());
        assertThat(response.getStatusCode().toString()).isEqualTo("200 OK");
        assertThat(response.getBody()).isNotNull();

        assertThrows(NoSuchElementException.class, () -> fileLinkService.downloadFileFromLink(UUID.randomUUID()));
    }

    @Test
    @Transactional
    void myLinks() throws IOException {
        uploadSomeFiles();

        List<String> emails = List.of("example@example.com", "example2@example.com");

        // Create public links
        fileLinkService.createPublicFileLink(TEST_USER_DIR + "/file1.txt");
        fileLinkService.createPrivateFileLink(TEST_USER_DIR + "/file2.txt", emails);
        fileLinkService.createPublicFileLink(TEST_USER_DIR + "/nested/file3.txt");

        // Show my links
        List<FileDTO> files = fileLinkService.myLinks();
        assertThat(files).isNotNull();
        assertThat(files).isNotEmpty();
        assertThat(files).hasSize(3);
        assertThat(files.get(2).metadata().name()).isEqualTo("file3.txt");
    }

    @Test
    @Transactional
    void linksSharedToMe() throws IOException {
        uploadSomeFiles();

        List<String> emails = List.of("example@example.com", "example2@example.com");

        // Create public links
        fileLinkService.createPrivateFileLink(TEST_USER_DIR + "/file1.txt", emails);
        fileLinkService.createPrivateFileLink(TEST_USER_DIR + "/file2.txt", emails);
        fileLinkService.createPrivateFileLink(TEST_USER_DIR + "/nested/file3.txt", emails);

        // Show links shared with me
        List<FileDTO> files = fileLinkService.linksSharedToMe();
        assertThat(files).isNotNull();
        assertThat(files).isNotEmpty();
        assertThat(files).hasSize(3);
        assertThat(files.get(2).metadata().name()).isEqualTo("file3.txt");
    }
}
