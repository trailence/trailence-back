package org.trailence.translations;

import org.springframework.stereotype.Service;
import org.trailence.external.aistudio.AIStudioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class TranslationService {
	
	private final AIStudioService ai;

	public Mono<String> translateWithAI(String text) {
		return this.ai.generateContent(text);
	}
	
}
