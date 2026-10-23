package com.liabletech.platform.files;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import com.liabletech.platform.AppProperties;
import com.liabletech.platform.web.NotFoundException;

/**
 * Keeps uploaded photos, PDFs and voice notes on local disk under {@code app.data-dir/files}, with
 * a database row per file. Files are only ever served after an account check.
 */
@Service
public class FileStore {

	private final JdbcClient jdbc;

	private final Path root;

	private final Clock clock;

	public FileStore(JdbcClient jdbc, AppProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.root = properties.dataDir().resolve("files").toAbsolutePath().normalize();
		this.clock = clock;
	}

	/**
	 * Stores the bytes. The content type is taken from the bytes themselves; files that are not a
	 * photo, PDF, audio or text file are rejected.
	 */
	public StoredFile save(Long accountId, byte[] bytes, String declaredType, String originalName) {
		String contentType = FileTypes.detect(bytes, declaredType, originalName);
		if (contentType == null) {
			throw new UnsupportedFileException("Only photos, PDFs, voice notes and CSV files can be uploaded.");
		}
		LocalDate today = LocalDate.now(clock);
		String key = "%d/%02d/%s%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(),
				FileTypes.extension(contentType));
		Path target = root.resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, bytes);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not store file", ex);
		}
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO stored_file (account_id, storage_key, original_name, content_type, size_bytes, sha256)
				VALUES (:accountId, :key, :name, :type, :size, :sha)
				""")
			.param("accountId", accountId)
			.param("key", key)
			.param("name", originalName == null ? null : Path.of(originalName).getFileName().toString())
			.param("type", contentType)
			.param("size", (long) bytes.length)
			.param("sha", sha256(bytes))
			.update(keys, "id");
		return get(keys.getKeyAs(Long.class));
	}

	public StoredFile get(long id) {
		return find(id).orElseThrow(() -> new NotFoundException("File " + id));
	}

	public Optional<StoredFile> find(long id) {
		return jdbc.sql("SELECT * FROM stored_file WHERE id = ?").param(id).query(StoredFile.class).optional();
	}

	/** The file, if it belongs to the account. */
	public StoredFile getForAccount(long accountId, long id) {
		StoredFile file = get(id);
		if (file.accountId() == null || file.accountId() != accountId) {
			throw new NotFoundException("File " + id);
		}
		return file;
	}

	public byte[] read(StoredFile file) {
		try {
			return Files.readAllBytes(pathOf(file));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read file " + file.id(), ex);
		}
	}

	public byte[] read(long id) {
		return read(get(id));
	}

	public void assignAccount(long fileId, long accountId) {
		jdbc.sql("UPDATE stored_file SET account_id = ? WHERE id = ? AND account_id IS NULL")
			.params(accountId, fileId)
			.update();
	}

	private Path pathOf(StoredFile file) {
		Path path = root.resolve(file.storageKey()).normalize();
		if (!path.startsWith(root)) {
			throw new NotFoundException("File " + file.id());
		}
		return path;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static class UnsupportedFileException extends RuntimeException {

		public UnsupportedFileException(String message) {
			super(message);
		}

	}

}
