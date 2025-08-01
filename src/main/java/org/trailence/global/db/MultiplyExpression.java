package org.trailence.global.db;

import org.springframework.data.relational.core.sql.Expression;

public class MultiplyExpression extends BinaryOperatorExpression {
	
	public MultiplyExpression(Expression left, Expression right) {
		super("*", left, right);
	}
	
}
