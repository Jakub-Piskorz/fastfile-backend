package com.fastfile.controller;

import com.fastfile.dto.FileDTO;
import com.fastfile.dto.PrivateFileLinkDTO;
import com.fastfile.model.FileLink;
import com.fastfile.service.FileLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/files/link")
public class FileShareController {


    private final FileLinkService fileLinkService;

    public FileShareController(FileLinkService fileLinkService) {
        this.fileLinkService = fileLinkService;
    }

    @PostMapping(path = "create", consumes = MediaType.TEXT_PLAIN_VALUE)
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
    public ResponseEntity<StreamingResponseBody> downloadFileFromLink(@PathVariable(name = "uuid") UUID uuid) throws IOException {
        return fileLinkService.downloadFileFromLink(uuid);
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
    public ResponseEntity<FileLink> updateFileLink(@PathVariable(name = "uuid") UUID uuid, @RequestBody List<String> emails) {
        FileLink fileLink = fileLinkService.updatePrivateLinkEmails(uuid, emails);
        if (fileLink != null) {
            return ResponseEntity.ok().body(fileLink);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/lookup/{uuid}")
    public ResponseEntity<FileDTO> lookupLinkFile(@PathVariable(name = "uuid") UUID uuid) throws IOException {
        FileDTO fileDTO = fileLinkService.lookupFile(uuid);
        if (fileDTO != null) {
            return ResponseEntity.ok().body(fileDTO);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<FileDTO>> getMyLinks() throws IOException {
        List<FileDTO> fileDTOs = fileLinkService.myLinks();
        return ResponseEntity.ok().body(fileDTOs);
    }

    @GetMapping("/shared-to-me")
    public ResponseEntity<List<FileDTO>> linksSharedToMe() throws IOException {
        List<FileDTO> fileDTOs = fileLinkService.linksSharedToMe();
        return ResponseEntity.ok().body(fileDTOs);
    }
}
