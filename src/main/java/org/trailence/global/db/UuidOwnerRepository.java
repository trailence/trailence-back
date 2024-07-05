package org.trailence.global.db;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NoRepositoryBean
public interface UuidOwnerRepository<T extends AbstractEntityUuidOwner> extends ReactiveCrudRepository<T, String> {

    Mono<T> findByUuidAndOwner(UUID uuid, String owner);

    Flux<T> findAllByUuidInAndOwner(Collection<UUID> uuids, String owner);
    
    Mono<Void> deleteByUuidAndOwner(UUID uuid, String owner);
    
    Mono<Void> deleteAllByUuidInAndOwner(Collection<UUID> uuids, String owner);

    Mono<Boolean> existsByUuidAndOwner(UUID uuid, String owner);

}
