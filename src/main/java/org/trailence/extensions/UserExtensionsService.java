package org.trailence.extensions;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.extensions.ExtensionsConfig.ContentValidation;
import org.trailence.extensions.ExtensionsConfig.ExtensionConfig;
import org.trailence.extensions.db.UserExtensionEntity;
import org.trailence.extensions.db.UserExtensionRepository;
import org.trailence.extensions.dto.UserExtension;
import org.trailence.global.TrailenceUtils;

import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserExtensionsService {

	private final UserExtensionRepository repo;
	private final ExtensionsConfig config;
	
	public List<String> getAllowedExtensions(boolean isAdmin, List<String> roles) {
		List<String> list = new LinkedList<>();
		for (var entry : config.getAllowed().entrySet()) {
			var cfg = entry.getValue();
			if (!cfg.isEnabled() ||
			   (!cfg.getRole().isEmpty() && !isAdmin && !roles.contains(cfg.getRole()))
			) continue;
			list.add(entry.getKey());
		}
		return list;
	}
	
	public Flux<UserExtension> syncMyExtensions(List<UserExtension> list, Authentication auth) {
		String email = auth.getPrincipal().toString();
		return repo.findByEmail(email).collectList()
			.flatMapMany(existing -> {
				List<Mono<?>> actions = new LinkedList<>();
				for (var dto : list.stream().filter(e -> valid(e, auth)).toList()) {
					if (dto.getVersion() == 0) handleCreation(dto, existing, email, actions);
					else if (dto.getVersion() == -1) handleDeletion(dto, existing, actions);
					else handleUpdate(dto, existing, actions);
				}
				if (actions.isEmpty()) return Flux.fromIterable(existing);
				return Flux.concat(actions).thenMany(repo.findByEmail(email));
			})
			.map(this::toDto);
	}
	
	private void handleCreation(UserExtension dto, List<UserExtensionEntity> existing, String email, List<Mono<?>> actions) {
		var optEntity = existing.stream().filter(entity -> entity.getExtension().equals(dto.getExtension())).findAny();
		if (optEntity.isEmpty()) {
			try {
				UserExtensionEntity entity = new UserExtensionEntity();
				entity.setId(UUID.randomUUID());
				entity.setVersion(0);
				entity.setEmail(email);
				entity.setExtension(dto.getExtension());
				entity.setData(Json.of(TrailenceUtils.mapper.writeValueAsString(dto.getData())));
				actions.add(repo.save(entity));
			} catch (Exception e) {
				// ignore
			}
		}
	}
	
	private void handleDeletion(UserExtension dto, List<UserExtensionEntity> existing, List<Mono<?>> actions) {
		var optEntity = existing.stream().filter(entity -> entity.getExtension().equals(dto.getExtension())).findAny();
		if (optEntity.isPresent())
			actions.add(repo.delete(optEntity.get()));
	}
	
	private void handleUpdate(UserExtension dto, List<UserExtensionEntity> existing, List<Mono<?>> actions) {
		var optEntity = existing.stream().filter(entity -> entity.getExtension().equals(dto.getExtension())).findAny();
		if (optEntity.isPresent()) {
			var entity = optEntity.get();
			if (entity.getVersion() != dto.getVersion()) return;
			try {
				String newData = TrailenceUtils.mapper.writeValueAsString(dto.getData());
				String existingData = TrailenceUtils.mapper.writeValueAsString(TrailenceUtils.mapper.readValue(entity.getData().asString(), Map.class));
				if (!newData.equals(existingData)) {
					entity.setData(Json.of(newData));
					actions.add(repo.save(entity));
				}
			} catch (Exception e) {
				// ignore
			}
		}
	}
	
	
	@SuppressWarnings("unchecked")
	private UserExtension toDto(UserExtensionEntity entity) {
		Map<String, String> data;
		try {
			data = TrailenceUtils.mapper.readValue(entity.getData().asString(), Map.class);
		} catch (Exception e) {
			data = new HashMap<>();
		}
		return new UserExtension(
			entity.getVersion(),
			entity.getExtension(),
			data
		);
	}
	
	@SuppressWarnings({"java:S3776"})
	private boolean valid(UserExtension dto, Authentication auth) {
		if (dto.getExtension() == null || dto.getExtension().isBlank()) return false;
		ExtensionConfig cfg = config.getAllowed().get(dto.getExtension());
		if (cfg == null || !cfg.isEnabled()) {
			log.info("Extension ignored because not enabled: {}", dto.getExtension());
			return false;
		}
		if (!cfg.getRole().isEmpty() && auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + cfg.getRole()) || a.getAuthority().equals(TrailenceUtils.AUTHORITY_ADMIN_USER))) {
			log.info("Extension ignored because user misses role: {}, {}", cfg.getRole());
			return false;
		}
		if (cfg.getContent() == null) return true;
		for (String key : dto.getData().keySet())
			if (!cfg.getContent().containsKey(key)) {
				log.info("Extension ignored because data {} is not configured", key);
				return false;
			}
		for (String key : cfg.getContent().keySet())
			if (!dto.getData().containsKey(key)) {
				log.info("Extension ignored because data {} is not present", key);
				return false;
			}
		for (Map.Entry<String, ContentValidation> entry : cfg.getContent().entrySet()) {
			String value = dto.getData().get(entry.getKey());
			if (value == null) {
				log.info("Extension ignored because data {} is null", entry.getKey());
				return false;
			}
			var validation = entry.getValue();
			if (validation.getPattern() != null && !validation.getPattern().isBlank() && !Pattern.matches(validation.getPattern(), value)) {
				log.info("Extension ignored because data {} does not match pattern", entry.getKey());
				return false;
			}
		}
		return true;
	}
}
