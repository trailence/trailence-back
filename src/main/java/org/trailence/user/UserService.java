package org.trailence.user;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;
import org.trailence.trail.db.TrailCollectionEntity;
import org.trailence.trail.dto.TrailCollectionType;
import org.trailence.user.db.UserEntity;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

	private final R2dbcEntityTemplate r2dbc;
	
	public Mono<Void> createUser(String email, String password) {
		UserEntity entity = new UserEntity(email.toLowerCase(), TrailenceUtils.hashPassword(password), System.currentTimeMillis());
		TrailCollectionEntity myTrails = new TrailCollectionEntity();
		myTrails.setUuid(UUID.randomUUID());
		myTrails.setOwner(email);
		myTrails.setType(TrailCollectionType.MY_TRAILS);
		myTrails.setName("");
		return r2dbc.insert(entity)
				.then(r2dbc.insert(myTrails))
				.then();
	}
	
}
