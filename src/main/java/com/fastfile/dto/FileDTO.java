package com.fastfile.dto;

import com.fastfile.model.FileLink;
import com.fastfile.model.FileMetadata;

public record FileDTO(FileMetadata metadata, FileLink fileLink) {}
