package org.trailence.global.db;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.sql.Assignment;
import org.springframework.data.relational.core.sql.Assignments;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Delete;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.relational.core.sql.Update;
import org.springframework.data.relational.core.sql.render.SqlRenderer;
import org.springframework.r2dbc.core.PreparedOperation;
import org.springframework.r2dbc.core.binding.BindMarker;
import org.springframework.r2dbc.core.binding.BindMarkers;
import org.springframework.r2dbc.core.binding.BindTarget;
import org.springframework.r2dbc.core.binding.Bindings;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("deprecation")
public final class DbUtils {

	public static PreparedOperation<Select> select(Select select, Bindings bindings, R2dbcEntityTemplate r2dbc) {
		return new PreparedOperation<Select>() {
			@Override
			public void bindTo(BindTarget target) {
				if (bindings != null)
					bindings.apply(target);
			}
			
			@Override
			public Select getSource() {
				return select;
			}
			
			@Override
			public String toQuery() {
				SqlRenderer sqlRenderer = SqlRenderer.create(r2dbc.getDataAccessStrategy().getStatementMapper().getRenderContext());
				return sqlRenderer.render(select);
			}
		};
	}

	public static PreparedOperation<Delete> delete(Delete delete, Bindings bindings, R2dbcEntityTemplate r2dbc) {
		return new PreparedOperation<Delete>() {
			@Override
			public void bindTo(BindTarget target) {
				if (bindings != null)
					bindings.apply(target);
			}
			
			@Override
			public Delete getSource() {
				return delete;
			}
			
			@Override
			public String toQuery() {
				SqlRenderer sqlRenderer = SqlRenderer.create(r2dbc.getDataAccessStrategy().getStatementMapper().getRenderContext());
				return sqlRenderer.render(delete);
			}
		};
	}
	
	public static <T> Mono<Long> updateByUuidAndOwner(R2dbcEntityTemplate r2dbc, T entity) {
		RelationalPersistentEntity<?> type = r2dbc.getConverter().getMappingContext().getRequiredPersistentEntity(entity.getClass());
		var accessor = type.getPropertyAccessor(entity);

		UUID uuid = (UUID) accessor.getProperty(type.getRequiredPersistentProperty("uuid"));
		String owner = (String) accessor.getProperty(type.getRequiredPersistentProperty("owner"));
		var versionProperty = Optional.ofNullable(type.getVersionProperty());
		
		Table table = Table.create(type.getQualifiedTableName());

		Condition where = Conditions.isEqual(Column.create("uuid", table), SQL.literalOf(uuid.toString()))
			.and(Conditions.isEqual(Column.create("owner", table), SQL.literalOf(owner)));
		if (versionProperty.isPresent())
			where = where.and(Conditions.isEqual(Column.create(versionProperty.get().getColumnName(), table), SQL.literalOf((Long) accessor.getProperty(versionProperty.get()))));
		
		var dialect = DialectResolver.getDialect(r2dbc.getDatabaseClient().getConnectionFactory());
		BindMarkers markers = dialect.getBindMarkersFactory().create();
		List<Pair<BindMarker, Object>> bindings = new LinkedList<>();
		
		List<Assignment> assignments = new LinkedList<>();
		type.forEach(p -> {
			if (p.isInsertOnly()) return;
			if (p.getName().equals("uuid") || p.getName().equals("owner")) return;
			if (p.equals(versionProperty.orElse(null))) {
				assignments.add(Assignments.value(Column.create(versionProperty.get().getColumnName(), table), SQL.literalOf(((Long) accessor.getProperty(versionProperty.get()) + 1))));
			} else if (p.getName().equals("updatedAt")) {
				BindMarker marker = markers.next();
				bindings.add(Pair.of(marker, r2dbc.getConverter().writeValue(System.currentTimeMillis(), p.getTypeInformation())));
				assignments.add(Assignments.value(Column.create(p.getColumnName(), table), SQL.bindMarker(marker.getPlaceholder())));
			} else {
				Object val = accessor.getProperty(p);
				val = r2dbc.getConverter().writeValue(val, p.getTypeInformation());
				if (val == null)
					assignments.add(Assignments.value(Column.create(p.getColumnName(), table), SQL.nullLiteral()));
				else {
					BindMarker marker = markers.next();
					bindings.add(Pair.of(marker, val));
					assignments.add(Assignments.value(Column.create(p.getColumnName(), table), SQL.bindMarker(marker.getPlaceholder())));
				}
			}
		});

		Update update = Update.builder()
		.table(table)
		.set(assignments)
		.where(where)
		.build();
		
		var operation = new PreparedOperation<Update>() {
			@Override
			public void bindTo(BindTarget target) {
				for (Pair<BindMarker, Object> binding : bindings)
					binding.getKey().bind(target, binding.getValue());			}
			
			@Override
			public Update getSource() {
				return update;
			}
			
			@Override
			public String toQuery() {
				SqlRenderer sqlRenderer = SqlRenderer.create(r2dbc.getDataAccessStrategy().getStatementMapper().getRenderContext());
				return sqlRenderer.render(update);
			}
		};
		
		return r2dbc.getDatabaseClient().sql(operation).fetch().rowsUpdated();
	}
	
}
