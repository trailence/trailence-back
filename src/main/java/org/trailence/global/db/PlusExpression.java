package org.trailence.global.db;

import org.springframework.data.relational.core.sql.Expression;

public class PlusExpression extends BinaryOperatorExpression {
	
	public PlusExpression(Expression left, Expression right) {
		super("+", left, right);
	}
	
}
