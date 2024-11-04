package org.trailence.extensions;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.extensions.db.UserExtensionEntity;
import org.trailence.extensions.db.UserExtensionRepository;
import org.trailence.extensions.dto.UserExtension;
import org.trailence.global.TrailenceUtils;

import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserExtensionsService {

	private final UserExtensionRepository repo;
	
	public Flux<UserExtension> syncMyExtensions(List<UserExtension> list, Authentication auth) {
		String email = auth.getPrincipal().toString();
		return repo.findByEmail(email).collectList()
			.flatMapMany(existing -> {
				List<Mono<?>> actions = new LinkedList<>();
				for (var dto : list) {
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
	
}
