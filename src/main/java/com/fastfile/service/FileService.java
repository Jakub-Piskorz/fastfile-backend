package com.fastfile.service;

import com.fastfile.dto.FileMetadataDTO;
import com.fastfile.dto.FilePathsDTO;
import com.fastfile.model.SharedFile;
import com.fastfile.model.SharedFileKey;
import com.fastfile.model.FileLink;
import com.fastfile.model.User;
import com.fastfile.repository.SharedFileRepository;
import com.fastfile.repository.FileLinkRepository;
import com.fastfile.repository.UserRepository;
import lombok.SneakyThrows;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.fastfile.service.FileSystemService.FILES_ROOT;

@Service
public class FileService {

    private final AuthService authService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final SharedFileRepository sharedFileRepository;
    private final FileLinkRepository fileLinkRepository;
    private final FileSystemService fileSystemService;


    public FileService(AuthService authService, UserService userService, UserRepository userRepository, SharedFileRepository sharedFileRepository, FileLinkRepository fileLinkRepository, FileSystemService fileSystemService) {
        this.authService = authService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.sharedFileRepository = sharedFileRepository;
        this.fileLinkRepository = fileLinkRepository;
        this.fileSystemService = fileSystemService;
    }

    Path getMyUserPath(String directory) {
        if (directory == null) directory = "";

        // 🔒 Safety check against unsafe paths.
        Path path = Paths.get(directory);
        if (directory.contains("\u0000") || path.isAbsolute()) {
            throw new IllegalArgumentException("Unsafe path");
        }
        Path normalized = path.normalize();
        for (Path part : normalized) {
            if (part.toString().equals("..")) {
                throw new IllegalArgumentException("Unsafe path");
            }
        }

        Long userId = authService.getMyUserId();
        return Paths.get(FILES_ROOT + userId + "/" + directory);
    }

    Path getMyUserPath() {
        return getMyUserPath("");
    }

    long bytesInside(Path path) throws IOException {
        if (path.toFile().isFile()) {
            return path.toFile().length();
        }
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(p -> p.toFile().isFile()).mapToLong(p -> p.toFile().length()).sum();
        }
    }

    boolean isMyStorageLimitExceeded(long newFileSize) {
        long currentUsage = userService.getMyUsedStorage();
        return (currentUsage + newFileSize) > userService.getMyUserStorageLimit();
    }

    public void updateMyUserStorage() throws IOException {
        long myCurrentUsage = bytesInside(getMyUserPath());
        User me = userService.getMe();
        me.setUsedStorage(myCurrentUsage);
        userRepository.save(me);
    }

    // Endpoint services

    public boolean uploadFile(MultipartFile file, String filePath) throws IOException {

        if (file == null || file.isEmpty()) {
            System.out.println("File doesn't exist.");
            return false;
        }

        if (isMyStorageLimitExceeded(file.getSize())) {
            System.out.println("Storage limit exceeded.");
            return false;
        }

        if (filePath == null) {
            filePath = "";
        }

        Path path = getMyUserPath(filePath).normalize();
        Path pathWithFile = getMyUserPath(filePath).resolve(Objects.requireNonNull(file.getOriginalFilename()));

        // Check if path exists
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        if (Files.exists(pathWithFile)) {
            System.out.println("File already exists: " + pathWithFile);
            return false;
        }
        Files.copy(file.getInputStream(), pathWithFile);

        updateMyUserStorage();
        return true;
    }

    public Set<FileMetadataDTO> filesInMyDirectory(String directory, int maxDepth) throws IOException {
        Path path = getMyUserPath(directory);

        Stream<Path> walkStream = Files.walk(path, maxDepth).skip(1);
        Set<FileMetadataDTO> filesMetadata = fileSystemService.getFilesMetadata(walkStream);
        walkStream.close();
        return filesMetadata;
    }

    public Set<FileMetadataDTO> filesInMyDirectory(String directory) throws IOException {
        return filesInMyDirectory(directory, 1);
    }

    public String createDirectory(String path) throws IOException {
        String errorMsg;
        Path pathForDir = getMyUserPath(path);
        // Check if path exists
        if (Files.exists(pathForDir)) {
            errorMsg = "Directory already exists";
            return errorMsg;
        }
        Files.createDirectories(pathForDir);
        return null;
    }

    public ResponseEntity<StreamingResponseBody> downloadFile(String filePath) throws IOException {
        Path fullFilePath = getMyUserPath(filePath);

        var file = fileSystemService.prepareFileForDownload(fullFilePath);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok().headers(file.headers()).body(file.body());
    }

    public ResponseEntity<StreamingResponseBody> downloadMultiple(FilePathsDTO filePaths) throws IOException {
        Path tempPath = Paths.get(FILES_ROOT + LocalTime.now().toString().replaceAll("[:.]", "-")).toAbsolutePath();
        String zipFileName = "/download.zip";
        Files.createDirectory(tempPath);

        final FileOutputStream fos = new FileOutputStream(tempPath + zipFileName);
        ZipOutputStream zipOut = new ZipOutputStream(fos);

        for (String filePath : filePaths.filePaths()) {
            Path finalFilePath = getMyUserPath(filePath);
            File fileToZip = new File(finalFilePath.toString());
            FileInputStream fis = new FileInputStream(fileToZip);
            ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
        }

        var zippedFile = fileSystemService.prepareFileForDownload(Paths.get(tempPath + zipFileName), () -> {
            // delete temp folder after streaming
            try {
                Files.walk(tempPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        zipOut.close();
        fos.close();
        return ResponseEntity.ok().headers(zippedFile.headers()).body(zippedFile.body());
    }

    public void delete(String filePath) throws IOException, NullPointerException {
        Path path = getMyUserPath(filePath).normalize();
        Files.delete(path);
        updateMyUserStorage();
    }

    public Iterable<FileMetadataDTO> searchFiles(String fileName, String directory) throws IOException {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("File name is empty");
        }
        Stream<Path> walkStream = Files.walk(getMyUserPath(directory == null ? "" : directory));
        // Skip(1), because it starts the list with itself (directory)
        Stream<Path> filteredWalkStream = walkStream.skip(1).filter(f -> f.getFileName().toString().contains(fileName));
        var filesMetadata = fileSystemService.getFilesMetadata(filteredWalkStream);
        walkStream.close();
        return filesMetadata;
    }

    @SneakyThrows
    public boolean deleteRecursively(String directory) {
        Path baseDir = getMyUserPath().toAbsolutePath();
        Path path = getMyUserPath(directory);
        if (path.toAbsolutePath().equals(baseDir)) {
            return false;
        }
        try (Stream<Path> walkStream = Files.walk(path)) {
            walkStream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // Log or handle the exception if needed
                    throw new UncheckedIOException(e);
                }
            });
        }

        updateMyUserStorage();
        return true;
    }

    public SharedFile shareFile(String path, Long targetUserId) {
        User me = userService.getMe();
        User targetUser = userRepository.findById(targetUserId).orElseThrow();

        SharedFileKey compositeId = new SharedFileKey();
        compositeId.setOwnerId(me.getId());
        compositeId.setSharedUserId(targetUserId);
        compositeId.setPath(path);

        if (sharedFileRepository.existsById(compositeId)) {
            throw new RuntimeException("File is already shared with this user.");
        }

        SharedFile sharedFile = new SharedFile();
        sharedFile.setId(compositeId);
        sharedFile.setSharedUser(targetUser);
        sharedFile.setOwner(me);
        sharedFileRepository.save(sharedFile);
        return sharedFile;
    }

    public FileLink shareLinkFile(String filePath) {
        String fullPath = getMyUserPath(filePath).normalize().toString();
        boolean fileExists = Files.exists(Paths.get(fullPath));
        if (!fileExists) {
            return null;
        }
        User me = userService.getMe();
        FileLink file;

        int attempts = 0;
        do {
            UUID randomUUID = UUID.randomUUID();
            file = new FileLink(randomUUID, me, fullPath);
            try {
                return fileLinkRepository.save(file);
            } catch (DataIntegrityViolationException e) {
                // UUID collision, try again
                attempts++;
                if (attempts > 5) {
                    throw new RuntimeException("Failed to generate unique UUID", e);
                }
            }
        } while (true);
    }

    public ResponseEntity<StreamingResponseBody> downloadLinkFile(UUID uuid) throws IOException {
        FileLink fileLink = fileLinkRepository.findById(uuid).orElseThrow();
        Path filePath = Paths.get(fileLink.getPath());

        var file = fileSystemService.prepareFileForDownload(filePath);

        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().headers(file.headers()).body(file.body());
    }

    // TODO: filePath filtering
    public Set<FileMetadataDTO> filesSharedByMe(String filePath) throws IOException {
        User me = userService.getMe();
        Set<String> sharedFilePaths = sharedFileRepository.findFilePathsSharedBy(me.getId());
        Set<FileMetadataDTO> filesMetadata = new HashSet<>();
        for (String sharedFilePath : sharedFilePaths) {
            filesMetadata.add(fileSystemService.getFileMetadata(getMyUserPath().resolve(sharedFilePath)));
        }
        return filesMetadata;
    }

    public Set<FileMetadataDTO> filesSharedByMe() throws IOException {
        return filesSharedByMe("");
    }

    public Set<FileMetadataDTO> filesSharedToMe() throws IOException {
        User me = userService.getMe();
        Set<SharedFile> sharedFiles = sharedFileRepository.findFilesSharedTo(me.getId());
        Set<FileMetadataDTO> filesMetadata = new HashSet<>();
        for (SharedFile sharedFile : sharedFiles) {
            filesMetadata.add(fileSystemService.getFileMetadata(Paths.get(FILES_ROOT + sharedFile.getOwnerId() + "/" + sharedFile.getPath())));
        }
        return filesMetadata;
    }
}
