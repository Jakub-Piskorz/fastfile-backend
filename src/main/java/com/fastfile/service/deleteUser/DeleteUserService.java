package com.fastfile.service.deleteUser;

import com.fastfile.model.FileLink;
import com.fastfile.model.FileLinkShare;
import com.fastfile.model.User;
import com.fastfile.repository.FileLinkRepository;
import com.fastfile.repository.FileLinkShareRepository;
import com.fastfile.repository.UserRepository;
import com.fastfile.service.FileSystemService;
import com.fastfile.service.UserService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Service
public class DeleteUserService {


    private final UserService userService;
    private final UserRepository userRepository;
    private final FileLinkRepository fileLinkRepository;
    private final FileSystemService fileSystemService;
    private final FileLinkShareRepository fileLinkShareRepository;


    public DeleteUserService(UserService userService, UserRepository userRepository, FileLinkRepository fileLinkRepository, FileSystemService fileSystemService, FileLinkShareRepository fileLinkShareRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.fileLinkRepository = fileLinkRepository;
        this.fileSystemService = fileSystemService;
        this.fileLinkShareRepository = fileLinkShareRepository;
    }

    public boolean deleteUser(User user) {
        Path myUserPath = userService.getMyUserPath().toAbsolutePath();
        boolean myPathExists = Files.exists(myUserPath);
        if (myPathExists) {
            fileSystemService.deleteRecursively(myUserPath);
            user.setUsedStorage(null);
            userRepository.save(user);
        }

        List<FileLink> myLinks = fileLinkRepository.findAllByOwnerId(user.getId());
        if (!myLinks.isEmpty()) {
            for (FileLink myLink : myLinks) {
                Set<FileLinkShare> linkShares = fileLinkShareRepository.findAllByFileLinkUuid(myLink.getUuid());
                if (!linkShares.isEmpty()) {
                    fileLinkShareRepository.deleteAll(linkShares);
                }
            }
            fileLinkRepository.deleteAll(myLinks);
        }
        userRepository.delete(user);
        return true;
    }

    public boolean deleteMe() {
        return deleteUser(userService.getMe());
    }
}
