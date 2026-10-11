package com.liabletech.bff.landing;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class LandingContentService {

	private final LandingContent content;

	public LandingContentService(ObjectMapper objectMapper,
			@Value("${liabletech.landing.content}") Resource source) throws IOException {
		try (InputStream in = source.getInputStream()) {
			this.content = objectMapper.readValue(in, LandingContent.class);
		}
	}

	public LandingContent getContent() {
		return content;
	}

}
