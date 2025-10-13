package com.fastfile.dto;

import com.fastfile.model.FileLink;

public record FileLinkDTO(FileMetadataDTO metadata, FileLink fileLink) {
}
