package org.trailence.global.db;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableFunction;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.trailence.global.dto.Versioned;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings({"java:S119"})
public final class BulkUtils {
	
	public static final int BULK_TO_SINGLE_PARALLEL = 2;
	public static final int BULK_TO_SINGLE_PREFETCH = BULK_TO_SINGLE_PARALLEL * 2;
	
	public static <SOURCE, RESULT> Mono<List<RESULT>> bulkToSingleOperationToList(Flux<SOURCE> source, Function<SOURCE, Mono<RESULT>> singleOperation, List<RESULT> existing, List<Throwable> errors) {
		return handleOperationsResult(parallelSingleOperations(source, singleOperation), existing, errors);
	}

	public static <SOURCE, RESULT> Mono<List<RESULT>> bulkToSingleOperationToList(Iterable<SOURCE> source, Function<SOURCE, Mono<RESULT>> singleOperation, List<RESULT> existing, List<Throwable> errors) {
		return bulkToSingleOperationToList(Flux.fromIterable(source), singleOperation, existing, errors);
	}
	
	public static <SOURCE, RESULT> Flux<Object> parallelSingleOperations(Flux<SOURCE> source, Function<SOURCE, Mono<RESULT>> singleOperation) {
		return source
			.publishOn(Schedulers.parallel())
			.flatMap(sourceItem ->
				singleOperation.apply(sourceItem)
					.map(Object.class::cast)
					.onErrorResume(e -> Mono.just((Object) e)),
				BULK_TO_SINGLE_PARALLEL, BULK_TO_SINGLE_PREFETCH
			);
	}
	
	@SuppressWarnings("unchecked")
	public static <RESULT> Mono<List<RESULT>> handleOperationsResult(Flux<Object> operationsResults, List<RESULT> existing, List<Throwable> errors) {
		return operationsResults.collectList()
		.flatMap(results -> {
			List<RESULT> success = new LinkedList<>(existing);
			results.forEach(result -> {
				if (result instanceof Throwable t)
					errors.add(t);
				else
					success.add((RESULT) result);
			});
			if (!success.isEmpty() || errors.isEmpty()) return Mono.just(success);
			return Mono.error(errors.getFirst());
		});
	}
	
	public static <T> Mono<List<T>> bulkFallbackWithSingleOperation(
		List<T> source,
		Function<List<T>, Mono<List<T>>> operation,
		List<T> existing,
		List<Throwable> errors
	) {
		return operation.apply(source)
		.onErrorResume(error -> {
			if (source.size() < 2) {
				if (existing.isEmpty())	return Mono.error(error);
				return Mono.just(existing);
			}
			return bulkToSingleOperationToList(
				source,
				item -> operation.apply(List.of(item)).map(result -> result.getFirst())
					.onErrorResume(DuplicateKeyException.class, e -> Mono.just(item)),
				existing,
				errors
			);
		});
	}
	
	public static <DTO extends Versioned.Interface, ENTITY extends AbstractEntityUuidOwner, VALIDATION extends Throwable> Mono<List<ENTITY>> bulkCreate(
		Iterable<DTO> source,
		String owner,
		FailableConsumer<DTO, VALIDATION> validateDto,
		Function<DTO, ENTITY> dtoToEntity,
		Function<List<ENTITY>, Mono<List<ENTITY>>> createEntities,
		UuidOwnerRepository<ENTITY> repo
	) {
		List<DTO> valid = new LinkedList<>();
    	Set<UUID> uuids = new HashSet<>();
    	List<Throwable> errors = new LinkedList<>();
    	source.forEach(dto -> {
    		try {
    			validateDto.accept(dto);
    			if (uuids.add(UUID.fromString(dto.getUuid())))
    				valid.add(dto);
    		} catch (@SuppressWarnings("java:S1181") Throwable t) {
    			errors.add(t);
    		}
    	});
    	if (valid.isEmpty()) {
    		if (errors.isEmpty()) return Mono.just(List.of());
    		return Mono.error(errors.getFirst());
    	}
    	
    	return repo.findAllByUuidInAndOwner(uuids, owner)
        .collectList()
        .flatMap(known -> {
            List<ENTITY> toCreate = valid.stream()
            	.filter(dto -> known.stream().noneMatch(entity -> entity.getUuid().toString().equals(dto.getUuid())))
            	.map(dtoToEntity)
            	.toList();
            if (toCreate.isEmpty()) {
            	if (!known.isEmpty() || errors.isEmpty()) return Mono.just(known);
            	return Mono.error(errors.getFirst()); // should never happen
            }
            return bulkFallbackWithSingleOperation(toCreate, createEntities, known, errors);
        });
	}
	
	public static <DTO extends Versioned.Interface, ENTITY extends AbstractEntityUuidOwner, VALIDATION extends Throwable> Flux<ENTITY> bulkUpdate(
		Iterable<DTO> source,
		String owner,
		FailableConsumer<DTO, VALIDATION> validateDto,
		TriFunction<ENTITY, DTO, ChecksAndActions, Boolean> entityUpdater,
		UuidOwnerRepository<ENTITY> repo,
		R2dbcEntityTemplate r2dbc
	) {
		return bulkUpdate(
			source,
			dto -> {
				validateDto.accept(dto);
				return UUID.fromString(dto.getUuid());
			},
			uuids -> repo.findAllByUuidInAndOwner(uuids, owner),
			(entity, dto) -> dto.getUuid().equals(entity.getUuid().toString()),
			(entity, dto) -> {
				if (dto.getVersion() != entity.getVersion()) return Mono.just(entity);
				ChecksAndActions checksAndActions = new ChecksAndActions();
				boolean updated = entityUpdater.apply(entity, dto, checksAndActions);
				if (!updated) return Mono.just(entity);
				return checksAndActions.execute()
				.then(DbUtils.updateByUuidAndOwner(r2dbc, entity))
	            .flatMap(nb -> nb == 0 ? Mono.empty() : repo.findByUuidAndOwner(entity.getUuid(), entity.getOwner()));
			}
		);
	}
	
	public static class ChecksAndActions {
		private Mono<Boolean> actions = Mono.just(true);
		private Mono<Optional<Throwable>> checks = Mono.just(Optional.empty());
		
		public ChecksAndActions addAction(Mono<?> action) {
			this.actions = this.actions.then(action.thenReturn(true));
			return this;
		}
		
		public ChecksAndActions addCheck(Mono<Optional<Throwable>> check) {
			checks = checks.flatMap(error -> {
        		if (error.isPresent()) return Mono.just(error);
        		return check;
        	});
			return this;
		}

		public Mono<Void> execute() {
			return checks.flatMap(error -> {
	        	if (error.isPresent()) return Mono.error(error.get());
	        	return actions.then();
	        });
		}
	}
	
	public static <SOURCE, UNIQUE, VALIDATION extends Throwable, ENTITY> Flux<ENTITY> bulkUpdate(
		Iterable<SOURCE> source,
		FailableFunction<SOURCE, UNIQUE, VALIDATION> validateItem,
		Function<Set<UNIQUE>, Flux<ENTITY>> fetchEntities,
		BiPredicate<ENTITY, SOURCE> sourceFinderForEntity,
		BiFunction<ENTITY, SOURCE, Mono<ENTITY>> entityUpdater
	) {
		List<SOURCE> valid = new LinkedList<>();
    	Set<UNIQUE> unique = new HashSet<>();
    	List<Throwable> errors = new LinkedList<>();
    	source.forEach(item -> {
    		try {
    			var uniqueId = validateItem.apply(item);
    			if (unique.add(uniqueId)) valid.add(item);
    		} catch (@SuppressWarnings("java:S1181") Throwable t) {
    			errors.add(t);
    		}
    	});
    	if (valid.isEmpty()) {
    		if (errors.isEmpty()) return Flux.empty();
    		return Flux.error(errors.getFirst());
    	}
        return fetchEntities.apply(unique)
        .flatMap(entity -> {
        	var itemOpt = valid.stream().filter(item -> sourceFinderForEntity.test(entity, item)).findAny();
            if (itemOpt.isEmpty()) return Mono.empty();
            var item = itemOpt.get();
            return entityUpdater.apply(entity, item);
        }, BULK_TO_SINGLE_PARALLEL, BULK_TO_SINGLE_PREFETCH)
        .switchIfEmpty(Flux.defer(() -> {
        	if (!errors.isEmpty()) return Flux.error(errors.getFirst());
        	return Flux.empty();
        }));
	}
}
