package com.fastfile.service;

import com.fastfile.dto.FileDTO;
import com.fastfile.dto.FileForDownloadDTO;
import com.fastfile.model.FileMetadata;
import com.fastfile.repository.FileLinkRepository;
import lombok.SneakyThrows;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class FileSystemService {
        private final FileLinkRepository fileLinkRepository;

    public FileSystemService(FileLinkRepository fileLinkRepository) {
        this.fileLinkRepository = fileLinkRepository;
    }

    public boolean isEmpty(Path path) {
        return Objects.requireNonNull(path.toFile().listFiles()).length == 0;
    }

    FileMetadata getFileMetadata(Path path) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class);
        boolean isDirectory = attrs.isDirectory();
        boolean hasFiles = false;
        if (isDirectory) {
            hasFiles = !isEmpty(path);
        }
        return new FileMetadata(
                path.getFileName().toString(),
                Files.size(path),
                attrs.lastModifiedTime().toMillis(),
                isDirectory ? "directory" : "file",
                path.toString(), hasFiles
        );
    }

    int isDirectoryCompare(FileDTO a, FileDTO b) {
        boolean aIsDir = Objects.equals(a.metadata().type(), "directory");
        boolean bIsDir = Objects.equals(b.metadata().type(), "directory");
        return Boolean.compare(bIsDir, aIsDir);
    }

    String getFileExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return (i > 0) ? fileName.substring(i + 1) : "";
    }

    List<FileDTO> getFilesDTO(Stream<Path> pathStream) {
        return pathStream.map(_path -> {
            try {
                var metadata = getFileMetadata(_path);
                var fileLink = fileLinkRepository.findByPath(_path.toString());
                return new FileDTO(metadata, fileLink);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).toList();
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

    public List<FileDTO> filesInDirectory(Path directory, int maxDepth) throws IOException {
        Stream<Path> walkStream = Files.walk(directory, maxDepth).skip(1);
        List<FileDTO> files = getFilesDTO(walkStream);
        walkStream.close();
        List<FileDTO> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort(this::isDirectoryCompare);
        return sortedFiles;
    }

    public String createDirectory(Path path) throws IOException {
        String errorMsg;
        // Check if path exists
        if (Files.exists(path)) {
            errorMsg = "Directory already exists";
            return errorMsg;
        }
        Files.createDirectories(path);
        return null;
    }

    @SneakyThrows
    public void deleteRecursively(Path path) {
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
    }

    FileForDownloadDTO prepareFileForDownload(Path path, Runnable afterStreamCallback) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }

        // Creating input stream from file
        StreamingResponseBody stream = out -> {
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            } finally {
                if (afterStreamCallback != null) {
                    afterStreamCallback.run(); // runs after streaming completes
                }
            }
        };

        // Preparing contentType for header
        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            String fileExtension = getFileExtension(path.getFileName().toString());
            contentType = getContentTypeFromExtension(fileExtension);
        }

        // Building headers for HTTP response
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, contentType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + path.getFileName().toString().replace("\"", "_") + "\"; filename*=UTF-8''" + URLEncoder.encode(path.getFileName().toString(), StandardCharsets.UTF_8)
        );
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.EXPIRES, "0");

        return new FileForDownloadDTO(stream, headers);
    }

    FileForDownloadDTO prepareFileForDownload(Path path) throws IOException {
        return prepareFileForDownload(path, null);
    }
}
