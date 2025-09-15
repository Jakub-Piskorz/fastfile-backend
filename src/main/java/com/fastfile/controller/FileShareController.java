package com.fastfile.controller;

import com.fastfile.dto.FileMetadataDTO;
import com.fastfile.dto.ShareFileDTO;
import com.fastfile.model.FileLink;
import com.fastfile.model.SharedFile;
import com.fastfile.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/files")
public class FileShareController {


    private final FileService fileService;

    public FileShareController(FileService fileService) {
        this.fileService = fileService;
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
