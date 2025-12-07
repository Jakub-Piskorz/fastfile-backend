package com.fastfile.controller;

import com.fastfile.dto.FileDTO;
import com.fastfile.dto.FilePathsDTO;
import com.fastfile.dto.SearchFileDTO;
import com.fastfile.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/list/{*path}")
    public ResponseEntity<List<FileDTO>> filesInDirectory(@PathVariable("path") String path) throws IOException {
        List<FileDTO> files = fileService.filesInMyDirectory(path);
        return new ResponseEntity<>(files, HttpStatus.OK);
    }

    @PostMapping("/search")
    public ResponseEntity<List<FileDTO>> searchFiles(@RequestBody SearchFileDTO searchFile) throws IOException {
        var files = fileService.searchFiles(searchFile.fileName(), searchFile.directory());
        return new ResponseEntity<>(files, HttpStatus.OK);
    }

    @GetMapping("/download/{*path}")
    public ResponseEntity<StreamingResponseBody> downloadFile(@PathVariable("path") String path) throws IOException {
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

    @DeleteMapping("/delete/{*path}")
    public ResponseEntity<String> removeFile(@PathVariable("path") String path) throws Exception {
        fileService.delete(path);
        return new ResponseEntity<>("Successfully deleted file.", HttpStatus.OK);
    }

    @DeleteMapping("/delete-recursively/{*path}")
    public ResponseEntity<String> deleteRecursively(@PathVariable("path") String path) {
        boolean result = fileService.deleteRecursively(path);
        if (result) {
            return new ResponseEntity<>("Successfully deleted file or folder.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Couldn't deleted file or folder.", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/create-directory/{*path}")
    public ResponseEntity<String> createDirectory(@PathVariable("path") String path) throws Exception {
        String errorMsg = fileService.createMyPersonalDirectory(path);
        if (errorMsg == null) {
            return new ResponseEntity<>("Successfully created directory.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>(errorMsg, HttpStatus.BAD_REQUEST);
        }
    }
}
