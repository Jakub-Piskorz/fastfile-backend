package com.fastfile.dto;

import lombok.NonNull;

public record DeleteFileDTO(@NonNull String path, boolean recursive) {
}
