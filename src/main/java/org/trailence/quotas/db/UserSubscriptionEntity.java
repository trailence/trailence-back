package org.trailence.quotas.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("user_subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscriptionEntity {
	
	@Id
	private UUID uuid;
	
	private String userEmail;
	private String planName;
	private long startsAt;
	private Long endsAt;

	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("user_subscriptions");
	public static final Column COL_PLAN_NAME = Column.create("plan_name", TABLE);
	public static final Column COL_STARTS_AT = Column.create("starts_at", TABLE);
	public static final Column COL_ENDS_AT = Column.create("ends_at", TABLE);
}
