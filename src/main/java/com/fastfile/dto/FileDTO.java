package com.fastfile.dto;

import com.fastfile.model.FileLink;
import com.fastfile.model.FileMetadata;
import io.swagger.v3.oas.annotations.media.Schema;

public record FileDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) FileMetadata metadata, FileLink fileLink) {}
