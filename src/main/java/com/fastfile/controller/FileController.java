package com.fastfile.controller;

import com.fastfile.dto.FileDTO;
import com.fastfile.dto.FilePathsDTO;
import com.fastfile.dto.SearchFileDTO;
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

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    public static final String FILES_ENDPOINT = "/api/v1/files";

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    public static String decodeURL(HttpServletRequest request, String path) {
        return URLDecoder.decode(request.getRequestURI().substring((FILES_ENDPOINT + path).length()), StandardCharsets.UTF_8);
    }

    @GetMapping("/list/**")
    public ResponseEntity<List<FileDTO>> filesInDirectory(HttpServletRequest request) throws IOException {
        var path = decodeURL(request, "/list/");
        List<FileDTO> files = fileService.filesInMyDirectory(path);
        return new ResponseEntity<>(files, HttpStatus.OK);
    }

    @PostMapping("/search")
    public ResponseEntity<List<FileDTO>> searchFiles(@RequestBody SearchFileDTO searchFile) throws IOException {
        var files = fileService.searchFiles(searchFile.fileName(), searchFile.directory());
        return new ResponseEntity<>(files, HttpStatus.OK);
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
}
