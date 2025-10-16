package com.fastfile.service;

import com.fastfile.dto.FileDTO;
import com.fastfile.model.FileMetadata;
import com.fastfile.model.FileLink;
import com.fastfile.model.FileLinkShare;
import com.fastfile.model.User;
import com.fastfile.repository.FileLinkRepository;
import com.fastfile.repository.FileLinkShareRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileLinkService {
    private final UserService userService;
    private final FileLinkRepository fileLinkRepository;
    private final FileSystemService fileSystemService;
    private final FileLinkShareRepository fileLinkShareRepository;


    public FileLinkService(UserService userService, FileLinkRepository fileLinkRepository, FileSystemService fileSystemService, FileLinkShareRepository fileLinkShareRepository) {
        this.userService = userService;
        this.fileLinkRepository = fileLinkRepository;
        this.fileSystemService = fileSystemService;
        this.fileLinkShareRepository = fileLinkShareRepository;
    }

    private FileLink createFileLink(String filePath, Boolean isPublic) {
        boolean linkAlreadyExists = fileLinkRepository.existsByPath(filePath);
        boolean fileExists = Files.exists(Paths.get(filePath));

        if (!fileExists || linkAlreadyExists) {
            return null;
        }

        User me = userService.getMe();
        FileLink file;

        int attempts = 0;
        do {
            UUID randomUUID = UUID.randomUUID();
            file = new FileLink(randomUUID, me, filePath, isPublic);
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

    @Transactional
    public FileLink createPrivateFileLink(String filePath, List<String> emails) {
        FileLink fileLink = createFileLink(filePath, false);
        if (fileLink == null) {
            return null;
        }

        // Create email shares in DB
        emails.forEach(email -> {
            FileLinkShare fileLinkShare = new FileLinkShare();
            fileLinkShare.setFileLink(fileLink);
            fileLinkShare.setSharedUserEmail(email);
            fileLinkShareRepository.save(fileLinkShare);
        });

        return fileLink;
    }

    @Transactional
    public FileLink updatePrivateLinkEmails(UUID uuid, Set<String> emails) {
        if (emails == null || uuid == null || uuid.toString().isEmpty() || emails.isEmpty()) return null;

        FileLink fileLink = fileLinkRepository.findById(uuid).orElse(null);
        if (fileLink == null) return null;

        Set<FileLinkShare> existingShares = fileLinkShareRepository.findAllByFileLink(fileLink);
        Set<String> existingEmails = existingShares.stream().map(FileLinkShare::getSharedUserEmail).collect(Collectors.toSet());

        Set<String> newEmails = new HashSet<>(emails);
        newEmails.removeAll(existingEmails);

        // Shares to add.
        Set<FileLinkShare> sharesToAdd = newEmails.stream().map(newEmail -> {
            FileLinkShare newShare = new FileLinkShare();
            newShare.setSharedUserEmail(newEmail);
            newShare.setFileLink(fileLink);
            return newShare;
        }).collect(Collectors.toSet());

        // Shares to remove
        Set<FileLinkShare> sharesToRemove = existingShares.stream()
                .filter(share -> !emails.contains(share.getSharedUserEmail()))
                .collect(Collectors.toSet());

        // Execute adds and removals of fileLinkShares.
        fileLinkShareRepository.deleteAll(sharesToRemove);
        fileLinkShareRepository.saveAll(sharesToAdd);

        return fileLink;
    }

    @Transactional
    public boolean removeFileLink(UUID uuid) {
        FileLink linkToRemove = fileLinkRepository.findById(uuid).orElse(null);
        if (linkToRemove == null) return false;
        if (!linkToRemove.getIsPublic()) fileLinkShareRepository.deleteAllByFileLink(linkToRemove);
        fileLinkRepository.delete(linkToRemove);
        return true;
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

    FileDTO linkToDTO(FileLink fileLink) throws IOException {
        FileMetadata metadata = fileSystemService.getFileMetadata(Paths.get(fileLink.getPath()));
        return new FileDTO(metadata, fileLink);
    }

    public FileDTO lookupFile(UUID uuid) throws IOException {
        FileLink fileLink = fileLinkRepository.findById(uuid).orElseThrow();
        return linkToDTO(fileLink);
    }

    public List<FileDTO> myLinks() throws IOException {
        List<FileLink> fileLinks = fileLinkRepository.findAllByOwnerId(userService.getMe().getId());
        List<FileDTO> DTOList = new ArrayList<>();
        for (FileLink fileLink : fileLinks) {
            FileDTO fileDTO = linkToDTO(fileLink);
            DTOList.add(fileDTO);
        }
        return DTOList;
    }

    public List<FileDTO> linksSharedToMe() throws IOException {
        User me = userService.getMe();
        List<FileLinkShare> sharedLinks = fileLinkShareRepository.findAllBySharedUserEmail(me.getEmail());
        List<FileDTO> fileDTOs = new ArrayList<>();
        for (FileLinkShare sharedLink : sharedLinks) {
            FileLink fileLink = sharedLink.getFileLink();
            FileDTO fileDTO = linkToDTO(fileLink);
            fileDTOs.add(fileDTO);
        }
        return fileDTOs;
    }
}
