package com.fastfile.controller;

import com.fastfile.dto.FileMetadataDTO;
import com.fastfile.dto.PrivateFileLinkDTO;
import com.fastfile.model.FileLink;
import com.fastfile.service.FileLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/files/link")
public class FileShareController {


    private final FileLinkService fileLinkService;

    public FileShareController(FileLinkService fileLinkService) {
        this.fileLinkService = fileLinkService;
    }

    @PostMapping("create")
    public ResponseEntity<FileLink> shareFileLink(@RequestBody String filePath) {
        FileLink fileLink = fileLinkService.createPublicFileLink(filePath);
        if (fileLink != null) {
            return ResponseEntity.ok().body(fileLink);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PostMapping("create-private")
    public ResponseEntity<FileLink> sharePrivateFileLink(@RequestBody PrivateFileLinkDTO privateFileLinkDTO) {
        FileLink fileLink = fileLinkService.createPrivateFileLink(privateFileLinkDTO.filePath(), privateFileLinkDTO.emails());
        if (fileLink != null) {
            return ResponseEntity.ok().body(fileLink);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<StreamingResponseBody> downloadLinkFile(@PathVariable(name = "uuid") UUID uuid) throws IOException {
        return fileLinkService.downloadLinkFile(uuid);
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<String> removeFileLink(@PathVariable(name = "uuid") UUID uuid) {
        var success = fileLinkService.removeFileLink(uuid);
        if (success) {
            return ResponseEntity.ok().body("Successfully deleted file fink.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Failed to delete file fink.");
        }
    }

    @PatchMapping("/{uuid}")
    public ResponseEntity<FileLink> updateFileLink(@PathVariable(name = "uuid") UUID uuid, @RequestBody Set<String> emails) {
        emails.forEach(email -> {});
        FileLink fileLink = fileLinkService.updatePrivateLinkEmails(uuid, emails);
        if (fileLink != null) {
            return ResponseEntity.ok().body(fileLink);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/lookup/{uuid}")
    public ResponseEntity<FileMetadataDTO> lookupLinkFile(@PathVariable(name = "uuid") UUID uuid) throws IOException {
        FileMetadataDTO fileMetadata = fileLinkService.lookupLinkFile(uuid);
        if (fileMetadata != null) {
            return ResponseEntity.ok().body(fileMetadata);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<FileMetadataDTO>> getMyLinks() throws IOException {
        List<FileMetadataDTO> fileMetadatas = fileLinkService.myLinks();
        return ResponseEntity.ok().body(fileMetadatas);
    }

    @GetMapping("/shared-to-me")
    public ResponseEntity<List<FileMetadataDTO>> linksSharedToMe() throws IOException {
        List<FileMetadataDTO> fileMetadatas = fileLinkService.linksSharedToMe();
        return ResponseEntity.ok().body(fileMetadatas);
    }
}
