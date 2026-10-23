package com.liabletech.platform.files;

import java.time.OffsetDateTime;

/** A photo, PDF or voice note kept on disk under the app's data directory. */
public record StoredFile(Long id, Long accountId, String storageKey, String originalName, String contentType,
		long sizeBytes, String sha256, OffsetDateTime createdAt) {

	public boolean isImage() {
		return contentType.startsWith("image/");
	}

	public boolean isPdf() {
		return "application/pdf".equals(contentType);
	}

	public boolean isAudio() {
		return contentType.startsWith("audio/");
	}

	/** A file name for downloads, from the original name or the type. */
	public String downloadName() {
		if (originalName != null && !originalName.isBlank()) {
			return originalName;
		}
		return "file-" + id + FileTypes.extension(contentType);
	}

}
