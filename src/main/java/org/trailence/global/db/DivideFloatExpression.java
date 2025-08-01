package org.trailence.global.db;

import org.springframework.data.relational.core.sql.Expression;

public class DivideFloatExpression extends BinaryOperatorExpression {
	
	public DivideFloatExpression(Expression left, Expression right) {
		super("/", new CastExpression(left, "numeric"), right);
	}
	
}
