package com.liabletech.platform.files;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.liabletech.platform.security.CurrentUser;

/** Serves a stored photo, PDF or voice note to users of the account that owns it. */
@Controller
public class FileController {

	private final FileStore files;

	public FileController(FileStore files) {
		this.files = files;
	}

	@GetMapping("/files/{id}")
	public ResponseEntity<byte[]> file(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(defaultValue = "false") boolean download) {
		StoredFile file = files.getForAccount(user.accountId(), id);
		ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
			.filename(file.downloadName(), java.nio.charset.StandardCharsets.UTF_8)
			.build();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(file.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
			.header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
			.header("X-Content-Type-Options", "nosniff")
			.body(files.read(file));
	}

}
