package com.fastfile.controller;

import com.fastfile.dto.FileMetadataDTO;
import com.fastfile.dto.FilePathsDTO;
import com.fastfile.dto.SearchFileDTO;
import com.fastfile.dto.ShareFileDTO;
import com.fastfile.model.SharedFile;
import com.fastfile.model.FileLink;
import com.fastfile.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    public final String FILES_ENDPOINT = "/api/v1/files";

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    private String decodeURL(HttpServletRequest request, String path) {
        return URLDecoder.decode(request.getRequestURI().substring((FILES_ENDPOINT + path).length()), StandardCharsets.UTF_8);
    }

    @GetMapping("/list/**")
    public ResponseEntity<List<FileMetadataDTO>> filesInDirectory(HttpServletRequest request) throws IOException {
        var path = decodeURL(request, "/list/");
        var files = fileService.filesInMyDirectory(path);
        return new ResponseEntity<>(files, HttpStatus.OK);
    }

    @PostMapping("/search")
    public Iterable<FileMetadataDTO> searchFiles(@RequestBody SearchFileDTO searchFile) throws IOException {
        return fileService.searchFiles(searchFile.fileName(), searchFile.directory());
    }

    @GetMapping("/download/**")
    public ResponseEntity<StreamingResponseBody> downloadFile(HttpServletRequest request) throws IOException {
        var path = decodeURL(request, "/download/");
        return fileService.downloadFile(path);
    }

    @PostMapping("/download-multiple")
    public ResponseEntity<StreamingResponseBody> downloadFile(@RequestBody FilePathsDTO filePaths) throws IOException {
        return fileService.downloadMultiple(filePaths);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart("filePath") String filePath) throws IOException {
        if (filePath == null) filePath = "";
        boolean success = fileService.uploadFile(file, filePath);
        if (success) {
            return new ResponseEntity<>("Successfully uploaded file.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Couldn't upload file", HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/delete/**")
    public ResponseEntity<String> removeFile(HttpServletRequest request) throws Exception {
        var filePath = decodeURL(request, "/delete/");
        fileService.delete(filePath);
        return new ResponseEntity<>("Successfully deleted file.", HttpStatus.OK);
    }

    @DeleteMapping("/delete-recursively/**")
    public ResponseEntity<String> deleteRecursively(HttpServletRequest request) {
        var filePath = decodeURL(request, "/delete-recursively/");
        boolean result = fileService.deleteRecursively(filePath);
        if (result) {
            return new ResponseEntity<>("Successfully deleted file or folder.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Couldn't deleted file or folder.", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/create-directory/**")
    public ResponseEntity<String> createDirectory(HttpServletRequest request) throws Exception {
        var filePath = decodeURL(request, "/create-directory/");
        String errorMsg = fileService.createMyPersonalDirectory(filePath);
        if (errorMsg == null) {
            return new ResponseEntity<>("Successfully created directory.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>(errorMsg, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/share")
    public ResponseEntity<SharedFile> shareFile(@RequestBody ShareFileDTO shareFileDTO) throws Exception {
        SharedFile sharedFile = fileService.shareFile(shareFileDTO.path(), shareFileDTO.targetUserId());
        if (sharedFile != null) {
            return new ResponseEntity<>(sharedFile, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/share-link")
    public ResponseEntity<FileLink> shareLinkFile(@RequestBody String filePath) {
        FileLink fileLink = fileService.shareLinkFile(filePath);
        if (fileLink != null) {
            return ResponseEntity.ok().body(fileLink);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/download-link/{uuid}")
    public ResponseEntity<StreamingResponseBody> downloadLinkFile(@PathVariable(name = "uuid") UUID uuid) throws IOException {
        return fileService.downloadLinkFile(uuid);
    }

    @GetMapping("/lookup-link/{uuid}")
    public ResponseEntity<FileMetadataDTO> lookupLinkFile(@PathVariable(name = "uuid") UUID uuid) throws IOException {
        FileMetadataDTO fileMetadata = fileService.lookupLinkFile(uuid);
        if (fileMetadata != null) {
            return ResponseEntity.ok().body(fileMetadata);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/my-links")
    public ResponseEntity<List<FileMetadataDTO>> getMyLinks() throws IOException {
        List<FileMetadataDTO> fileMetadatas = fileService.myLinks();
        if (fileMetadatas != null && !fileMetadatas.isEmpty()) {
            return ResponseEntity.ok().body(fileMetadatas);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/shared-by-me")
    public ResponseEntity<List<FileMetadataDTO>> filesSharedByMe() throws Exception {
        List<FileMetadataDTO> files = fileService.filesSharedByMe();
        if (files != null) {
            return new ResponseEntity<>(files, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/shared-to-me")
    public ResponseEntity<List<FileMetadataDTO>> filesSharedToMe() throws Exception {
        List<FileMetadataDTO> files = fileService.filesSharedToMe();
        if (files != null) {
            return new ResponseEntity<>(files, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }
}
