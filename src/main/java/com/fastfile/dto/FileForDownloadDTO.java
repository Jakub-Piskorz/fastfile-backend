package com.fastfile.dto;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public record FileForDownloadDTO(StreamingResponseBody body, HttpHeaders headers) {}