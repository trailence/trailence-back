package org.trailence.donations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.donations.db.DonationEntity;
import org.trailence.donations.db.DonationGoalEntity;
import org.trailence.donations.db.DonationGoalRepository;
import org.trailence.donations.db.DonationRepository;
import org.trailence.donations.dto.CreateDonationRequest;
import org.trailence.donations.dto.Donation;
import org.trailence.donations.dto.DonationGoal;
import org.trailence.donations.dto.DonationStatus;
import org.trailence.external.CurrencyConverterService;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.dto.PageResult;
import org.trailence.global.exceptions.NotFoundException;

import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class DonationService {
	
	private final R2dbcEntityTemplate r2dbc;
	private final DonationRepository donationRepo;
	private final DonationGoalRepository goalRepo;
	private final CurrencyConverterService currencyConverterService;

	@Transactional
	public Mono<Donation> createDonation(CreateDonationRequest request) {
		return Mono.defer(() -> {
			BigDecimal amount = new BigDecimal(request.getAmount());
			BigDecimal realAmount = request.getRealAmount() != null ? new BigDecimal(request.getRealAmount()) : BigDecimal.ZERO;
			String email = request.getEmail() != null ? request.getEmail().toLowerCase().trim() : "";
			long timestamp = request.getTimestamp() != null ? request.getTimestamp() : System.currentTimeMillis();
			Mono<Long> amountInEuros = convertAmount(amount, request.getAmountCurrency());
			Mono<Long> realAmountInEuros = convertAmount(realAmount, request.getRealAmountCurrency());
			return donationRepo.findByPlatformAndPlatformId(request.getPlatform(), request.getPlatformId())
			.switchIfEmpty(
				amountInEuros.zipWhen(a -> realAmountInEuros)
				.flatMap(tuple -> {
					DonationEntity entity = new DonationEntity(UUID.randomUUID(), request.getPlatform(), request.getPlatformId(), timestamp, tuple.getT1(), tuple.getT2(), email, request.getDetails());
					return r2dbc.insert(entity);
				})
			)
			.map(this::toDto);
		});
	}
	
	private Mono<Long> convertAmount(BigDecimal amount, String currency) {
		if (amount.equals(BigDecimal.ZERO)) return Mono.just(0L);
		if ("eur".equalsIgnoreCase(currency)) return Mono.just(amount.multiply(BigDecimal.valueOf(1000000)).longValue());
		return currencyConverterService.convertToEuro(currency, amount).map(a -> a.multiply(BigDecimal.valueOf(1000000)).longValue());
	}
	
	private Donation toDto(DonationEntity entity) {
		return new Donation(
			entity.getUuid().toString(),
			entity.getPlatform(),
			entity.getPlatformId(),
			entity.getTimestamp(),
			entity.getAmount(),
			entity.getRealAmount(),
			entity.getEmail(),
			entity.getDetails()
		);
	}
	
	private static final Map<String, Object> donationDtoFieldMapping = new HashMap<>();
	
	static {
		donationDtoFieldMapping.put("uuid", DonationEntity.COL_UUID);
		donationDtoFieldMapping.put("platform", DonationEntity.COL_PLATFORM);
		donationDtoFieldMapping.put("platformId", DonationEntity.COL_PLATFORM_ID);
		donationDtoFieldMapping.put("timestamp", DonationEntity.COL_TIMESTAMP);
		donationDtoFieldMapping.put("amount", DonationEntity.COL_AMOUNT);
		donationDtoFieldMapping.put("realAmount", DonationEntity.COL_REAL_AMOUNT);
		donationDtoFieldMapping.put("email", DonationEntity.COL_EMAIL);
		donationDtoFieldMapping.put("details", DonationEntity.COL_DETAILS);
	}

	
	public Mono<PageResult<Donation>> getDonations(Pageable pageable) {
		String sql = new SqlBuilder()
		.select(AsteriskFromTable.create(DonationEntity.TABLE))
		.from(DonationEntity.TABLE)
		.pageable(pageable, donationDtoFieldMapping)
		.build();
		return Mono.zip(
			r2dbc.query(DbUtils.operation(sql, null), this::toDonationDto).all().collectList().publishOn(Schedulers.parallel()),
			donationRepo.count().publishOn(Schedulers.parallel())
		).map(result -> new PageResult<Donation>(pageable, result.getT1(), result.getT2()));
	}
	
	private Donation toDonationDto(Row row) {
		return new Donation(
			row.get(DonationEntity.COL_UUID.getName().toString(), UUID.class).toString(),
			row.get(DonationEntity.COL_PLATFORM.getName().toString(), String.class),
			row.get(DonationEntity.COL_PLATFORM_ID.getName().toString(), String.class),
			row.get(DonationEntity.COL_TIMESTAMP.getName().toString(), Long.class),
			row.get(DonationEntity.COL_AMOUNT.getName().toString(), Long.class),
			row.get(DonationEntity.COL_REAL_AMOUNT.getName().toString(), Long.class),
			row.get(DonationEntity.COL_EMAIL.getName().toString(), String.class),
			row.get(DonationEntity.COL_DETAILS.getName().toString(), String.class)
		);
	}
	
	@Transactional
	public Mono<Donation> updateDonation(Donation dto) {
		return donationRepo.findById(UUID.fromString(dto.getUuid()))
		.switchIfEmpty(Mono.error(new NotFoundException("donation", dto.getUuid())))
		.flatMap(entity -> {
			entity.setAmount(dto.getAmount());
			entity.setRealAmount(dto.getRealAmount());
			entity.setEmail(dto.getEmail());
			return donationRepo.save(entity);
		})
		.map(this::toDto);
	}
	
	@Transactional
	public Mono<Void> deleteDonation(String uuid) {
		return donationRepo.findById(UUID.fromString(uuid))
		.switchIfEmpty(Mono.error(new NotFoundException("donation", uuid)))
		.flatMap(donationRepo::delete);
	}
	
	@Transactional
	public Mono<List<DonationGoal>> getGoals() {
		return goalRepo.findAll().map(this::toDto).collectList();
	}
	
	private DonationGoal toDto(DonationGoalEntity entity) {
		return new DonationGoal(
			entity.getIndex(),
			entity.getType(),
			entity.getAmount(),
			entity.getCreatedAt(),
			entity.getUpdatedAt()
		);
	}
	
	@Transactional
	public Mono<List<DonationGoal>> updateGoals(List<DonationGoal> goals) {
		return goalRepo.deleteAll().thenMany(Flux.fromIterable(goals)).flatMap(g -> r2dbc.insert(toEntity(g))).map(this::toDto).collectList();
	}
	
	private DonationGoalEntity toEntity(DonationGoal dto) {
		return new DonationGoalEntity(
			dto.getIndex(),
			dto.getType(),
			dto.getAmount(),
			dto.getCreatedAt(),
			dto.getUpdatedAt()
		);
	}
	
	@Transactional
	public Mono<DonationStatus> getStatus() {
		return Mono.zip(
			getCurrentDonations().publishOn(Schedulers.parallel()),
			goalRepo.findAll().map(this::toDto).collectList().publishOn(Schedulers.parallel())
		).map(tuple -> new DonationStatus(tuple.getT1(), tuple.getT2()));
	}
	
	private Mono<Long> getCurrentDonations() {
		String sql = "SELECT COALESCE(SUM(" + DonationEntity.COL_REAL_AMOUNT + "), 0) FROM " + DonationEntity.TABLE;
		return r2dbc.query(DbUtils.operation(sql, null), row -> row.get(0, Long.class)).one();
	}
}
