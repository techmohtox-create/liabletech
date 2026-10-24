package com.liabletech.parts.voice;

import java.util.Optional;

import com.liabletech.platform.files.StoredFile;

/** Turns a mechanic's voice note into words, when a transcription service is set up. */
public interface SpeechToText {

	/** False when no service is configured; voice notes are then played at the counter. */
	boolean available();

	/** The words spoken, or empty if the service could not be reached or heard nothing. */
	Optional<String> transcribe(StoredFile audio, byte[] bytes);

}
