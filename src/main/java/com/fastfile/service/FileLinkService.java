package com.fastfile.service;

import com.fastfile.dto.FileMetadataDTO;
import com.fastfile.model.FileLink;
import com.fastfile.model.FileLinkShare;
import com.fastfile.model.User;
import com.fastfile.repository.FileLinkRepository;
import com.fastfile.repository.FileLinkShareRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileLinkService {
    private final UserService userService;
    private final FileLinkRepository fileLinkRepository;
    private final FileSystemService fileSystemService;
    private final FileLinkShareRepository fileLinkShareRepository;
    private final FileService fileService;


    public FileLinkService(UserService userService, FileLinkRepository fileLinkRepository, FileSystemService fileSystemService, FileLinkShareRepository fileLinkShareRepository, FileService fileService) {
        this.userService = userService;
        this.fileLinkRepository = fileLinkRepository;
        this.fileSystemService = fileSystemService;
        this.fileLinkShareRepository = fileLinkShareRepository;
        this.fileService = fileService;
    }

    private FileLink createFileLink(String filePath, Boolean isPublic) {
        String fullPath = fileService.getMyUserPath(filePath).normalize().toString();

        FileLink fileLink = fileLinkRepository.findByPath(fullPath).orElse(null);
        if (fileLink != null) {
            return fileLink;
        }

        boolean fileExists = Files.exists(Paths.get(fullPath));
        if (!fileExists) {
            return null;
        }
        User me = userService.getMe();
        FileLink file;

        int attempts = 0;
        do {
            UUID randomUUID = UUID.randomUUID();
            file = new FileLink(randomUUID, me, fullPath, isPublic);
            try {
                return fileLinkRepository.save(file);
            } catch (DataIntegrityViolationException e) {
                // UUID collision, try again
                attempts++;
                if (attempts > 5) {
                    throw new RuntimeException("Failed to generate unique UUID", e);
                }
            }
        } while (true);
    }

    public FileLink createPublicFileLink(String filePath) {
        return createFileLink(filePath, true);
    }

    public FileLink createPrivateFileLink(String filePath, List<String> emails) {
        FileLink fileLink = createFileLink(filePath, false);
        assert fileLink != null;

        emails.forEach(email -> {
            FileLinkShare fileLinkShare = new FileLinkShare();
            fileLinkShare.setFileLink(fileLink);
            fileLinkShare.setSharedUserEmail(email);
            fileLinkShareRepository.save(fileLinkShare);
        });
        return fileLink;
    }

    public ResponseEntity<StreamingResponseBody> downloadLinkFile(UUID uuid) throws IOException {
        FileLink fileLink = fileLinkRepository.findById(uuid).orElseThrow();
        Path filePath = Paths.get(fileLink.getPath());

        var file = fileSystemService.prepareFileForDownload(filePath);

        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().headers(file.headers()).body(file.body());
    }

    public FileMetadataDTO lookupLinkFile(UUID uuid) throws IOException {
        FileLink fileLink = fileLinkRepository.findById(uuid).orElseThrow();
        return fileSystemService.getFileMetadata(Paths.get(fileLink.getPath()));
    }

    public List<FileMetadataDTO> myLinks() throws IOException {
        List<FileLink> fileLinks = fileLinkRepository.findAllByOwnerId(userService.getMe().getId());
        ArrayList<FileMetadataDTO> fileMetadatas = new ArrayList<>();
        for (FileLink fileLink : fileLinks) {
            FileMetadataDTO metadata = lookupLinkFile(fileLink.getUuid());
            fileMetadatas.add(metadata);
        }
        return fileMetadatas;
    }

    public List<FileMetadataDTO> linksSharedToMe() throws IOException {
        User me = userService.getMe();
        List<FileLinkShare> sharedLinks = fileLinkShareRepository.findAllBySharedUserEmail(me.getEmail());
        ArrayList<FileMetadataDTO> fileMetadatas = new ArrayList<>();
        for (FileLinkShare sharedLink : sharedLinks) {
            FileLink fileLink = sharedLink.getFileLink();
            FileMetadataDTO metadata = lookupLinkFile(fileLink.getUuid());
            fileMetadatas.add(metadata);
        }
        return fileMetadatas;
    }
}
