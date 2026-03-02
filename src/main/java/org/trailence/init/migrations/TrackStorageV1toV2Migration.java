package org.trailence.init.migrations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.r2dbc.core.binding.MutableBindings;
import org.trailence.global.db.DbUtils;
import org.trailence.init.Migration;
import org.trailence.trail.TrackStorage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrackStorageV1toV2Migration implements Migration {
	@Override
	public String id() {
		return "1.5_track_storage_v2";
	}
	
	@Override
	@SuppressWarnings("java:S112")
	public void execute(R2dbcEntityTemplate db, ApplicationContext context) throws Exception {
		var dialect = DialectResolver.getDialect(db.getDatabaseClient().getConnectionFactory());
		
		long startTime = System.currentTimeMillis();
		log.info("Migrating tracks to v2");
		long nb = db.getDatabaseClient().sql("SELECT uuid,owner,data FROM tracks ORDER BY uuid,owner")
		.fetch()
		.all()
		.flatMap(row -> {
			try {
				UUID uuid = (UUID) row.get("uuid");
				String owner = (String) row.get("owner");
				ByteBuffer bb = (ByteBuffer) row.get("data");
				var v2 = migrate(bb);
				MutableBindings bindings = new MutableBindings(dialect.getBindMarkersFactory().create());
				StringBuilder sql = new StringBuilder(512);
				var dataBinding = bindings.bind(ByteBuffer.wrap(v2));
				var uuidBinding = bindings.bind(uuid);
				var ownerBinding = bindings.bind(owner);
				sql.append("UPDATE tracks SET data = ").append(dataBinding.getPlaceholder()).append(" WHERE uuid = ").append(uuidBinding.getPlaceholder()).append(" AND owner = ").append(ownerBinding.getPlaceholder());
				return db.getDatabaseClient().sql(DbUtils.operation(sql.toString(), bindings)).fetch().rowsUpdated();
			} catch (IOException e) {
				throw new RuntimeException("IO error", e);
			}
		})
		.count()
		.block();
		log.info("{} tracks migrated in {}", nb, System.currentTimeMillis() - startTime);

		startTime = System.currentTimeMillis();
		nb = db.getDatabaseClient().sql("SELECT trail_uuid, data FROM public_tracks ORDER BY trail_uuid")
		.fetch()
		.all()
		.flatMap(row -> {
			try {
				UUID uuid = (UUID) row.get("trail_uuid");
				ByteBuffer bb = (ByteBuffer) row.get("data");
				var v2 = migrate(bb);
				MutableBindings bindings = new MutableBindings(dialect.getBindMarkersFactory().create());
				StringBuilder sql = new StringBuilder(512);
				var dataBinding = bindings.bind(ByteBuffer.wrap(v2));
				var uuidBinding = bindings.bind(uuid);
				sql.append("UPDATE public_tracks SET data = ").append(dataBinding.getPlaceholder()).append(" WHERE trail_uuid = ").append(uuidBinding.getPlaceholder());
				return db.getDatabaseClient().sql(DbUtils.operation(sql.toString(), bindings)).fetch().rowsUpdated();
			} catch (IOException e) {
				throw new RuntimeException("IO error", e);
			}
		})
		.count()
		.block();
		log.info("{} public tracks migrated in {}", nb, System.currentTimeMillis() - startTime);
	}
	
	private byte[] migrate(ByteBuffer source) throws IOException {
		byte[] data = new byte[source.remaining()];
		source.get(data);
		var v1 = TrackStorage.V1.uncompress(data);
		return TrackStorage.V1V2Bridge.v1DtoToV2(v1);
	}
}
