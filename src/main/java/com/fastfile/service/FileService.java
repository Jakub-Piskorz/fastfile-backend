package com.fastfile.service;

import com.fastfile.dto.FileDTO;
import com.fastfile.dto.FilePathsDTO;
import com.fastfile.model.FileLink;
import com.fastfile.model.FileLinkShare;
import com.fastfile.model.User;
import com.fastfile.repository.FileLinkRepository;
import com.fastfile.repository.FileLinkShareRepository;
import com.fastfile.repository.UserRepository;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.fastfile.service.FileSystemService.FILES_ROOT;

@Service
public class FileService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final FileLinkRepository fileLinkRepository;
    private final FileSystemService fileSystemService;
    private final FileLinkShareRepository fileLinkShareRepository;


    public FileService(UserService userService, UserRepository userRepository, FileLinkRepository fileLinkRepository, FileSystemService fileSystemService, FileLinkShareRepository fileLinkShareRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.fileLinkRepository = fileLinkRepository;
        this.fileSystemService = fileSystemService;
        this.fileLinkShareRepository = fileLinkShareRepository;
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
        long myStorageLimit = userService.getMyUserStorageLimit();
        return (currentUsage + newFileSize) > myStorageLimit;
    }

    public void updateUserStorage(long userId) throws IOException {
        long myCurrentUsage = bytesInside(userService.getMyUserPath());
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        user.setUsedStorage(myCurrentUsage);
        userRepository.save(user);
    }

    public void updateMyUserStorage() throws IOException {
        updateUserStorage(userService.getMe().getId());
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

        Path path = userService.getMyUserPath(filePath).normalize();
        Path pathWithFile = userService.getMyUserPath(filePath).resolve(Objects.requireNonNull(file.getOriginalFilename()));

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

    public List<FileDTO> filesInMyDirectory(String directory, int maxDepth) throws IOException {
        Path path = userService.getMyUserPath(directory);
        return fileSystemService.filesInDirectory(path, maxDepth);
    }

    public List<FileDTO> filesInMyDirectory(String directory) throws IOException {
        return filesInMyDirectory(directory, 1);
    }

    public String createMyPersonalDirectory(String path) throws IOException {
        Path pathForDir = userService.getMyUserPath(path);
        return fileSystemService.createDirectory(pathForDir);
    }

    public ResponseEntity<StreamingResponseBody> downloadFile(String filePath) throws IOException {
        Path fullFilePath = userService.getMyUserPath(filePath);

        var file = fileSystemService.prepareFileForDownload(fullFilePath);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok().headers(file.headers()).body(file.body());
    }

    public ResponseEntity<StreamingResponseBody> downloadMultiple(FilePathsDTO filePaths) throws IOException {
        if (filePaths.filePaths().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Path tempPath = Paths.get(FILES_ROOT + LocalTime.now().toString().replaceAll("[:.]", "-")).toAbsolutePath();
        String zipFileName = "/download.zip";
        Files.createDirectory(tempPath);

        final FileOutputStream fos = new FileOutputStream(tempPath + zipFileName);
        ZipOutputStream zipOut = new ZipOutputStream(fos);

        for (String filePath : filePaths.filePaths()) {
            Path finalFilePath = userService.getMyUserPath(filePath);
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
            zipOut.closeEntry();
        }
        zipOut.close();

        @SuppressWarnings("ResultOfMethodCallIgnored")
        var zippedFile = fileSystemService.prepareFileForDownload(Paths.get(tempPath + zipFileName), () -> {
            // delete temp folder after streaming
            try (Stream<Path> stream = Files.walk(tempPath)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                zipOut.close();
                fos.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return ResponseEntity.ok().headers(zippedFile.headers()).body(zippedFile.body());
    }

    public void delete(String filePath) throws IOException, NullPointerException {
        Path path = userService.getMyUserPath(filePath).normalize();
        Set<FileLink> fileLinks = fileLinkRepository.findAllByPath(path.toString());

        // Remove every link from database
        if (!fileLinks.isEmpty()) {
            for (FileLink fileLink : fileLinks) {
                Set<FileLinkShare> privateShares = fileLinkShareRepository.findAllByFileLinkUuid(fileLink.getUuid());
                if (!privateShares.isEmpty()) {
                    fileLinkShareRepository.deleteAll(privateShares);
                }
                fileLinkRepository.delete(fileLink);
            }
        }

        Files.delete(path);
        updateMyUserStorage();
    }

    public List<FileDTO> searchFiles(String fileName, String directory) throws IOException {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("File name is empty");
        }
        Stream<Path> walkStream = Files.walk(userService.getMyUserPath(directory == null ? "" : directory));
        // Skip(1), because it starts the list with itself (directory)
        Stream<Path> filteredWalkStream = walkStream.skip(1).filter(f -> f.getFileName().toString().contains(fileName));
        List<FileDTO> fileDTOs = fileSystemService.getFilesDTO(filteredWalkStream);
        walkStream.close();
        return fileDTOs;
    }

    public List<FileDTO> searchFiles(String fileName) throws IOException {
        return searchFiles(fileName, null);
    }

    @SneakyThrows
    public boolean deleteRecursively(String directory) {
        Path baseDir = userService.getMyUserPath().toAbsolutePath();
        Path finalPath = userService.getMyUserPath(directory);
        if (finalPath.toAbsolutePath().equals(baseDir)) {
            return false;
        }
        fileSystemService.deleteRecursively(finalPath);
        updateMyUserStorage();
        return true;
    }
}
