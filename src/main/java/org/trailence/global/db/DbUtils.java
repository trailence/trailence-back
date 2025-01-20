package org.trailence.global.db;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

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
import org.springframework.data.relational.core.sql.render.RenderContext;
import org.springframework.data.relational.core.sql.render.SqlRenderer;
import org.springframework.r2dbc.core.PreparedOperation;
import org.springframework.r2dbc.core.binding.BindTarget;
import org.springframework.r2dbc.core.binding.Bindings;
import org.springframework.r2dbc.core.binding.MutableBindings;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("deprecation")
public final class DbUtils {

	public static PreparedOperation<Select> select(Select select, Bindings bindings, R2dbcEntityTemplate r2dbc) {
		return preparedOperation(select, bindings, () -> getRenderer(r2dbc).render(select));
	}

	public static PreparedOperation<Delete> delete(Delete delete, Bindings bindings, R2dbcEntityTemplate r2dbc) {
		return preparedOperation(delete, bindings, () -> getRenderer(r2dbc).render(delete));
	}

	public static PreparedOperation<Update> update(Update update, Bindings bindings, R2dbcEntityTemplate r2dbc) {
		return preparedOperation(update, bindings, () -> getRenderer(r2dbc).render(update));
	}
	
	public static PreparedOperation<String> operation(String sql, Bindings bindings) {
		return preparedOperation(sql, bindings, () -> sql);
	}
	
	private static <T> PreparedOperation<T> preparedOperation(T source, Bindings bindings, Supplier<String> toQuery) {
		return new PreparedOperation<T>() {
			@Override
			public void bindTo(BindTarget target) {
				if (bindings != null)
					bindings.apply(target);
			}
			
			@Override
			public T getSource() {
				return source;
			}
			
			@Override
			public String toQuery() {
				return toQuery.get();
			}
		};
	}
	
	private static final String PROPERTY_UUID = "uuid";
	private static final String PROPERTY_OWNER = "owner";
	
	@SuppressWarnings("java:S3776") // complexity
	public static <T> Mono<Long> updateByUuidAndOwner(R2dbcEntityTemplate r2dbc, T entity) {
		RelationalPersistentEntity<?> type = r2dbc.getConverter().getMappingContext().getRequiredPersistentEntity(entity.getClass());
		var accessor = type.getPropertyAccessor(entity);

		UUID uuid = (UUID) accessor.getProperty(type.getRequiredPersistentProperty(PROPERTY_UUID));
		String owner = (String) accessor.getProperty(type.getRequiredPersistentProperty(PROPERTY_OWNER));
		if (uuid == null || owner == null) return Mono.just(0L);
		var versionProperty = Optional.ofNullable(type.getVersionProperty());
		
		Table table = Table.create(type.getQualifiedTableName());
		
		Condition where = Conditions.isEqual(Column.create(PROPERTY_UUID, table), SQL.literalOf(uuid.toString()))
			.and(Conditions.isEqual(Column.create(PROPERTY_OWNER, table), SQL.literalOf(owner)));
		if (versionProperty.isPresent())
			where = where.and(Conditions.isEqual(Column.create(versionProperty.get().getColumnName(), table), SQL.literalOf((Long) accessor.getProperty(versionProperty.get()))));
		
		var dialect = DialectResolver.getDialect(r2dbc.getDatabaseClient().getConnectionFactory());
		MutableBindings bindings = new MutableBindings(dialect.getBindMarkersFactory().create()); 
		
		List<Assignment> assignments = new LinkedList<>();
		type.forEach(p -> {
			if (p.isInsertOnly()) return;
			if (p.getName().equals(PROPERTY_UUID) || p.getName().equals(PROPERTY_OWNER)) return;
			if (p.equals(versionProperty.orElse(null))) {
				assignments.add(Assignments.value(Column.create(versionProperty.get().getColumnName(), table), SQL.literalOf(((Long) accessor.getProperty(versionProperty.get()) + 1))));
			} else if (p.getName().equals("updatedAt")) {
				var marker = bindings.bind(r2dbc.getConverter().writeValue(System.currentTimeMillis(), p.getTypeInformation()));
				assignments.add(Assignments.value(Column.create(p.getColumnName(), table), SQL.bindMarker(marker.getPlaceholder())));
			} else {
				Object val = accessor.getProperty(p);
				val = r2dbc.getConverter().writeValue(val, p.getTypeInformation());
				if (val == null)
					assignments.add(Assignments.value(Column.create(p.getColumnName(), table), SQL.nullLiteral()));
				else {
					var marker = bindings.bind(val);
					assignments.add(Assignments.value(Column.create(p.getColumnName(), table), SQL.bindMarker(marker.getPlaceholder())));
				}
			}
		});

		Update update = Update.builder()
		.table(table)
		.set(assignments)
		.where(where)
		.build();
		
		var operation = update(update, bindings, r2dbc);
		return r2dbc.getDatabaseClient().sql(operation).fetch().rowsUpdated();
	}
	
	private static SqlRenderer getRenderer(R2dbcEntityTemplate r2dbc) {
		RenderContext ctx = r2dbc.getDataAccessStrategy().getStatementMapper().getRenderContext();
		return ctx != null ? SqlRenderer.create(ctx) : SqlRenderer.create();
	}
	
}
