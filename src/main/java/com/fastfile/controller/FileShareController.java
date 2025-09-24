package com.fastfile.controller;

import com.fastfile.dto.FileMetadataDTO;
import com.fastfile.dto.PrivateFileLinkDTO;
import com.fastfile.model.FileLink;
import com.fastfile.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/files/link")
public class FileShareController {


    private final FileService fileService;

    public FileShareController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("create")
    public ResponseEntity<FileLink> shareFileLink(@RequestBody String filePath) {
        FileLink fileLink = fileService.createPublicFileLink(filePath);
        if (fileLink != null) {
            return ResponseEntity.ok().body(fileLink);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PostMapping("create-private")
    public ResponseEntity<FileLink> sharePrivateFileLink(@RequestBody PrivateFileLinkDTO privateFileLinkDTO) {
        FileLink fileLink = fileService.createPrivateFileLink(privateFileLinkDTO.filePath(), privateFileLinkDTO.emails());
        if (fileLink != null) {
            return ResponseEntity.ok().body(fileLink);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<StreamingResponseBody> downloadLinkFile(@PathVariable(name = "uuid") UUID uuid) throws IOException {
        return fileService.downloadLinkFile(uuid);
    }

    @GetMapping("/lookup/{uuid}")
    public ResponseEntity<FileMetadataDTO> lookupLinkFile(@PathVariable(name = "uuid") UUID uuid) throws IOException {
        FileMetadataDTO fileMetadata = fileService.lookupLinkFile(uuid);
        if (fileMetadata != null) {
            return ResponseEntity.ok().body(fileMetadata);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<FileMetadataDTO>> getMyLinks() throws IOException {
        List<FileMetadataDTO> fileMetadatas = fileService.myLinks();
        if (fileMetadatas != null && !fileMetadatas.isEmpty()) {
            return ResponseEntity.ok().body(fileMetadatas);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
}
