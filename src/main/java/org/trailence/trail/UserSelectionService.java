package org.trailence.trail;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.trail.db.UserSelectionEntity;
import org.trailence.trail.db.UserSelectionRepository;
import org.trailence.trail.dto.UserSelection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSelectionService {

	private final UserSelectionRepository repo;
	private final R2dbcEntityTemplate r2dbc;
	
	public Mono<List<UserSelection>> getMySelection(Authentication auth) {
		return repo.findById(auth.getPrincipal().toString())
		.map(selection -> {
			try {
				return new ObjectMapper().readValue(selection.getSelection().asArray(), new TypeReference<List<UserSelection>>() {});
			} catch (Exception e) {
				log.error("Invalid user selection", e);
				return List.<UserSelection>of();
			}
		})
		.switchIfEmpty(Mono.just(List.<UserSelection>of()));
	}
	
	@Transactional
	public Mono<List<UserSelection>> createSelection(List<UserSelection> newSelection, Authentication auth) {
		return repo.findByIdForUpdate(auth.getPrincipal().toString())
		.flatMap(entity -> {
			try {
				var mapper = new ObjectMapper();
				List<UserSelection> current = mapper.readValue(entity.getSelection().asArray(), new TypeReference<List<UserSelection>>() {});
				List<UserSelection> newList = new ArrayList<>(current.size() + newSelection.size());
				newList.addAll(current);
				for (var newSel : newSelection) {
					if (!newList.contains(newSel)) newList.add(newSel);
				}
				entity.setSelection(Json.of(mapper.writeValueAsBytes(newList)));
				return repo.save(entity).thenReturn(newList);
			} catch (Exception e) {
				return Mono.error(e);
			}
		})
		.switchIfEmpty(Mono.defer(() -> {
			try {
				UserSelectionEntity entity = new UserSelectionEntity();
				entity.setEmail(auth.getPrincipal().toString());
				entity.setSelection(Json.of(new ObjectMapper().writeValueAsBytes(newSelection)));
				return r2dbc.insert(entity).thenReturn(newSelection);
			} catch (Exception e) {
				return Mono.error(e);
			}
		}));
	}
	
	@Transactional
	public Mono<Void> deleteSelection(List<UserSelection> selectionToDelete, Authentication auth) {
		return repo.findByIdForUpdate(auth.getPrincipal().toString())
		.flatMap(entity -> {
			try {
				var mapper = new ObjectMapper();
				List<UserSelection> current = mapper.readValue(entity.getSelection().asArray(), new TypeReference<List<UserSelection>>() {});
				List<UserSelection> newList = new ArrayList<>(current.size());
				for (var sel : current) {
					if (!selectionToDelete.contains(sel)) newList.add(sel);
				}
				entity.setSelection(Json.of(mapper.writeValueAsBytes(newList)));
				return repo.save(entity);
			} catch (Exception e) {
				return Mono.error(e);
			}
		})
		.then();
	}
	
}
