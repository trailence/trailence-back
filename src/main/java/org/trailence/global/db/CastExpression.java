package org.trailence.global.db;

import org.springframework.data.relational.core.sql.Expression;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CastExpression implements Expression {

	private final Expression expression;
	private final String castTo;
	
	@Override
	public String toString() {
		return expression.toString() + "::" + castTo;
	}
}
