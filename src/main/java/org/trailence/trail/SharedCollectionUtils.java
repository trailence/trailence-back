package org.trailence.trail;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.trail.db.SharedCollectionMemberRepository;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SharedCollectionUtils {

	public static final String SHARED_OWNER_PREFIX = "@share@";
	public static final int MIN_VERSION_FOR_SHARED = 20299;
	
	public static boolean isSharedCollectionOwner(String owner) {
		return owner != null && owner.startsWith(SHARED_OWNER_PREFIX);
	}
	public static UUID getSharedCollectionUuid(String owner) {
		if (!isSharedCollectionOwner(owner)) throw new BadRequestException("Expected a shared collection owner, but found " + owner);
		return UUID.fromString(owner.substring(SHARED_OWNER_PREFIX.length()));
	}
	
	
	@Data
	@AllArgsConstructor
	public static class Owner {
		private String user;
		private String authorForUser;
		private String authorInDb;
	}
	
	public static Mono<Owner> getOwner(Optional<String> trailOwner, Authentication auth, SharedCollectionMemberRepository sharedCollectionMemberRepo) {
		String user = TrailenceUtils.email(auth);
		String authorForCaller = trailOwner.map(o -> o.toLowerCase()).orElse(user);
		if (authorForCaller.equals(user)) return Mono.just(new Owner(user, user, user));
		if (!SharedCollectionUtils.isSharedCollectionOwner(authorForCaller)) return Mono.error(new ForbiddenException());
		return sharedCollectionMemberRepo.findByUuidAndOwner(SharedCollectionUtils.getSharedCollectionUuid(authorForCaller), user)
			.map(member -> new Owner(user, authorForCaller, SharedCollectionUtils.SHARED_OWNER_PREFIX + member.getSharedCollectionUuid()))
			.switchIfEmpty(Mono.error(new NotFoundException("shared-collection", authorForCaller)));
	}

}
