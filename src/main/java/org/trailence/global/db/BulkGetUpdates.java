package org.trailence.global.db;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Select;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.UuidAndOwner;
import org.trailence.global.dto.Versioned;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BulkGetUpdates {

	public static <E extends AbstractEntityUuidOwner, R, K> Mono<UpdateResponse<R>> bulkGetUpdates(R2dbcEntityTemplate r2dbc, List<Select> selectAccessible, Class<E> entityClass, Function<E, K> uniqueKeyExtractor, List<Versioned> known, Function<E, R> mapper) {
		return bulkGetUpdates(
			Flux.concat(selectAccessible.stream().map(select -> r2dbc.query(DbUtils.select(select, null, r2dbc), entityClass).all()).toList())
				.distinct(uniqueKeyExtractor),
            known,
            mapper
        );
	}

	public static <E extends AbstractEntityUuidOwner, R> Mono<UpdateResponse<R>> bulkGetUpdates(R2dbcEntityTemplate r2dbc, Select selectAccessible, Class<E> entityClass, List<Versioned> known, Function<E, R> mapper) {
		return bulkGetUpdates(
			r2dbc.query(DbUtils.select(selectAccessible, null, r2dbc), entityClass).all(),
            known,
            mapper
        );
	}
	
	@SuppressWarnings("java:S2445") // synchronized on parameter
	public static <E extends AbstractEntityUuidOwner, R> Mono<UpdateResponse<R>> bulkGetUpdates(Flux<E> entities, List<Versioned> known, Function<E, R> mapper) {
        List<E> newItems = new LinkedList<>();
        List<E> updatedItems = new LinkedList<>();

        return entities
        .doOnNext(entity -> {
            Optional<Versioned> knownOpt;
            synchronized (known) {
                knownOpt = known.stream().filter(v -> v.getUuid().equals(entity.getUuid().toString()) && v.getOwner().equals(entity.getOwner())).findAny();
            }
            if (knownOpt.isEmpty()) {
                synchronized (newItems) {
                    newItems.add(entity);
                }
            } else {
                Versioned v = knownOpt.get();
                synchronized (known) {
                    known.remove(v);
                }
                if (v.getVersion() < entity.getVersion()) {
                    synchronized (updatedItems) {
                        updatedItems.add(entity);
                    }
                }
            }
        })
        .then(Mono.fromSupplier(() -> {
            UpdateResponse<R> response = new UpdateResponse<>();
            response.setDeleted(known.stream().map(v -> new UuidAndOwner(v.getUuid(), v.getOwner())).toList());
            response.setCreated(newItems.stream().map(mapper).toList());
            response.setUpdated(updatedItems.stream().map(mapper).toList());
            return response;
        }));
	}
	
}
