package com.scenary.media;

public record VideoUploadCreateRequest(String fileName, Long sizeBytes, String mime) {
}
