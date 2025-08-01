package org.trailence.global.db;

import org.springframework.data.relational.core.sql.Expression;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BinaryOperatorExpression implements Expression {

	private final String operator;
	private final Expression left;
	private final Expression right;
	
	@Override
	public String toString() {
		return "(" + left.toString() + " " + operator + " " + right.toString() + ")";
	}
	
}
