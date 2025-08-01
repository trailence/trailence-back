package org.trailence.global.db;

import org.springframework.data.relational.core.sql.Expression;

public class MinusExpression extends BinaryOperatorExpression {
	
	public MinusExpression(Expression left, Expression right) {
		super("-", left, right);
	}
	
}
