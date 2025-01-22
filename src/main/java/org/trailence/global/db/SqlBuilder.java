package org.trailence.global.db;

import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.stream.Streams;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.relational.core.sql.Aliased;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public class SqlBuilder {
	
	private final StringBuilder sql = new StringBuilder();
	
	public SqlBuilder select(Expression... fields) {
		sql.append("SELECT ");
		append(fields);
		return this;
	}
	
	public SqlBuilder from(@NonNull Table table) {
		return from(table, null);
	}
	
	public SqlBuilder from(@NonNull Table table, @Nullable String alias) {
		sql.append(" FROM ");
		sql.append(table.toString());
		if (alias != null) sql.append(" AS ").append(alias);
		return this;
	}
	
	public SqlBuilder from(@NonNull String innerSql, @NonNull String alias) {
		sql.append(" FROM (").append(innerSql).append(")");
		sql.append(" AS ").append(alias);
		return this;
	}
	
	public SqlBuilder leftJoinSubSelect(@NonNull String innerSql, @NonNull Condition on, @Nullable String alias) {
		return leftJoin("(" + innerSql + ")", on, alias);
	}
	
	public SqlBuilder leftJoinTable(@NonNull Table table, @NonNull Condition on, @Nullable String alias) {
		return leftJoin(table.toString(), on, alias);
	}
	
	private SqlBuilder leftJoin(String join, Condition on, String alias) {
		sql.append(" LEFT JOIN ").append(join);
		if (alias != null) sql.append(" AS ").append(alias);
		sql.append(" ON ").append(on.toString());
		return this;
	}
	
	public SqlBuilder where(Condition condition) {
		sql.append(" WHERE ").append(condition.toString());
		return this;
	}
	
	public SqlBuilder groupBy(Expression... expressions) {
		sql.append(" GROUP BY ");
		append(expressions);
		return this;
	}
	
	public SqlBuilder pageable(@NonNull Pageable pageable, @NonNull Map<String, Object> sortFieldMapping) {
		if (pageable.getSort().isSorted())
			orderBy(pageable.getSort(), sortFieldMapping);
		if (pageable.isPaged())
			sql.append(" LIMIT ").append(pageable.getPageSize()).append(" OFFSET ").append(pageable.getOffset());
		return this;
	}
	
	public SqlBuilder orderBy(Iterable<Order> orders, Map<String, Object> sortFieldMapping) {
		Iterator<Order> it = orders.iterator();
		if (!it.hasNext()) return this;
		sql.append(" ORDER BY ");
		sql.append(String.join(",", Streams.of(it).map(o -> order(o, sortFieldMapping)).filter(o -> o != null).toList()));
		return this;
	}
	
	private String order(Order order, Map<String, Object> sortFieldMapping) {
		StringBuilder result = new StringBuilder();
		Object field = sortFieldMapping.get(order.getProperty());
		if (field == null) return null;
		switch (field) {
			case CharSequence s -> result.append(s);
			case Expression e -> append(e);
			default -> { return null; }
		}
		result.append(' ').append(order.isAscending() ? "ASC" : "DESC");
		return result.toString();
	}
	
	public String build() {
		return sql.toString();
	}
	
	private void append(Expression... expressions) {
		boolean first = true;
		for (Expression e : expressions) {
			if (first) first = false;
			else sql.append(',');
			append(e);
		}
	}
	
	private void append(Expression e) {
		sql.append(e.toString());
		if (e instanceof Aliased a) sql.append(" AS ").append(a.getAlias());
	}
	
}
