package org.trailence.global.db;

import java.util.Iterator;
import java.util.Map;

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
	
	public SqlBuilder select(@NonNull Table table, Expression... fields) {
		sql.append("SELECT ");
		append(fields);
		sql.append(" FROM ");
		sql.append(table.toString());
		return this;
	}
	
	public SqlBuilder leftJoin(@NonNull String joinSql, @NonNull Condition on, @Nullable String alias) {
		sql.append(" LEFT JOIN ").append(joinSql);
		if (alias != null) sql.append(" AS ").append(alias);
		sql.append(" ON ").append(on.toString());
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
		boolean first = true;
		for (Iterator<Order> it = orders.iterator(); it.hasNext(); ) {
			if (first) {
				sql.append(" ORDER BY ");
				first = false;
			} else {
				sql.append(',');
			}
			Order order = it.next();
			Object field = sortFieldMapping.get(order.getProperty());
			switch (field) {
				case CharSequence s -> sql.append(s);
				case Expression e -> append(e);
				default -> { continue; }
			}
			sql.append(' ').append(order.isAscending() ? "ASC" : "DESC");
		}
		return this;
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
