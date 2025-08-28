package com.fastfile.dto;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;

public record FileForDownloadDTO(InputStreamResource resource, HttpHeaders headers) {}