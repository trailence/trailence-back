package org.trailence.donations.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("donations")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DonationEntity {

	@Id
	private UUID uuid;
	
	private String platform;
	private String platformId;
	
	private long timestamp;
	private long amount;
	private long realAmount;
	private String email;
	
	private String details;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("donations");
	public static final Column COL_UUID = Column.create("uuid", TABLE);
	public static final Column COL_PLATFORM = Column.create("platform", TABLE);
	public static final Column COL_PLATFORM_ID = Column.create("platform_id", TABLE);
	public static final Column COL_TIMESTAMP = Column.create("timestamp", TABLE);
	public static final Column COL_AMOUNT = Column.create("amount", TABLE);
	public static final Column COL_REAL_AMOUNT = Column.create("real_amount", TABLE);
	public static final Column COL_EMAIL = Column.create("email", TABLE);
	public static final Column COL_DETAILS = Column.create("details", TABLE);

}
