package com.fastfile.service;

import com.fastfile.dto.FileForDownloadDTO;
import com.fastfile.dto.FileMetadataDTO;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileSystemService {

    FileMetadataDTO getFileMetadata(Path path) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class);
        return new FileMetadataDTO(path.getFileName().toString(), Files.size(path), attrs.lastModifiedTime().toMillis(), Files.isDirectory(path) ? "directory" : "file",  // Type
                path.toString());
    }

    String getFileExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return (i > 0) ? fileName.substring(i + 1) : "";
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

    String getContentTypeFromExtension(String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    FileForDownloadDTO prepareFileForDownload(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }

        InputStream inputStream = Files.newInputStream(path);
        InputStreamResource resource = new InputStreamResource(inputStream);

        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            String fileExtension = getFileExtension(path.getFileName().toString());
            contentType = getContentTypeFromExtension(fileExtension);
        }

        // Building headers for HTTP response
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, contentType);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; fileName=\"" + URLEncoder.encode(path.getFileName().toString(), StandardCharsets.UTF_8) + "\"");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.EXPIRES, "0");

        return new FileForDownloadDTO(resource, headers);
    }
}
