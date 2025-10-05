package com.fastfile.repository;

import com.fastfile.model.FileLink;
import com.fastfile.model.FileLinkShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileLinkShareRepository extends JpaRepository<FileLinkShare, Long> {
    List<FileLinkShare> findAllByFileLink(FileLink fileLink);
    List<FileLinkShare> findAllBySharedUserEmail(String sharedUserEmail);

}
