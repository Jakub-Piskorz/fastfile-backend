package com.fastfile.service;

import com.fastfile.dto.FileMetadataDTO;
import com.fastfile.dto.SearchFileDTO;
import com.fastfile.model.SharedFile;
import com.fastfile.model.SharedFileKey;
import com.fastfile.model.User;
import com.fastfile.repository.SharedFileRepository;
import com.fastfile.repository.UserRepository;
import lombok.SneakyThrows;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileService {
    public static final String FILES_ROOT = "files/";

    private final AuthService authService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final SharedFileRepository sharedFileRepository;

    public FileService(AuthService authService, UserService userService, UserRepository userRepository, SharedFileRepository sharedFileRepository) {
        this.authService = authService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.sharedFileRepository = sharedFileRepository;
    }

    FileMetadataDTO getFileMetadata(Path path) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class);
        return new FileMetadataDTO(
                path.getFileName().toString(),
                Files.size(path),
                attrs.lastModifiedTime().toMillis(),
                Files.isDirectory(path) ? "directory" : "file",  // Type
                path.toString()
        );
    }

    Set<FileMetadataDTO> getFilesMetadata(Stream<Path> pathStream) {
        return pathStream.map(_path -> {
            try {
                return getFileMetadata(_path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
    }

    String getFileExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return (i > 0) ? fileName.substring(i + 1) : "";
    }

    String getContentTypeFromExtension(String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
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

        String userId = authService.getMyUserId();
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
            return stream.filter(p -> p.toFile().isFile())
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        }
    }

    boolean isMyStorageLimitExceeded(long newFileSize) {
        long currentUsage = userService.getMyUserStorage();
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

    public Set<FileMetadataDTO> filesInDirectory(String directory, int maxDepth) throws IOException {
        Path path = getMyUserPath(directory);

        Stream<Path> walkStream = Files.walk(path, maxDepth).skip(1);
        Set<FileMetadataDTO> filesMetadata = getFilesMetadata(walkStream);
        walkStream.close();
        return filesMetadata;
    }

    public Set<FileMetadataDTO> filesInDirectory(String directory) throws IOException {
        return filesInDirectory(directory, 1);
    }

    public ResponseEntity<InputStreamResource> downloadFile(String directory) throws IOException {
        Path filePath = getMyUserPath(directory);

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        InputStream inputStream = Files.newInputStream(filePath);
        InputStreamResource resource = new InputStreamResource(inputStream);

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            String fileExtension = getFileExtension(filePath.getFileName().toString());
            contentType = getContentTypeFromExtension(fileExtension);
        }

        // Building headers for HTTP response
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, contentType);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; fileName=\"" + URLEncoder.encode(filePath.getFileName().toString(), StandardCharsets.UTF_8) + "\"");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.EXPIRES, "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    public void delete(String filePath) throws IOException, NullPointerException {
        Path path = getMyUserPath(filePath).normalize();
        Files.delete(path);
        updateMyUserStorage();
    }

    public Iterable<FileMetadataDTO> searchFiles(SearchFileDTO searchFile) throws IOException {
        if (searchFile.fileName() == null || searchFile.fileName().isEmpty()) {
            throw new IllegalArgumentException("File name is empty");
        }
        Stream<Path> walkStream = Files.walk(getMyUserPath(searchFile.directory() == null ? "" : searchFile.directory()));
        // Skip(1), because it starts the list with itself (directory)
        Stream<Path> filteredWalkStream = walkStream.skip(1).filter(f -> f.getFileName().toString().contains(searchFile.fileName()));
        var filesMetadata = getFilesMetadata(filteredWalkStream);
        walkStream.close();
        return filesMetadata;
    }

    public boolean createDirectory(String path) throws IOException {
        if (path != null && !Files.exists(getMyUserPath(path))) {
            Files.createDirectories(getMyUserPath(path));
            return true;
        } else {
            return false;
        }
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
}
