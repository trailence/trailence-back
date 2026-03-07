package org.trailence.preferences.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("user_community")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCommunityEntity {

	@Id
	private String email;
	private UUID publicUuid;
	private int nbPublications;
	private int nbComments;
	private int nbRates;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("user_community");
	public static final Column COL_EMAIL = Column.create("email", TABLE);
	public static final Column COL_NB_PUBLICATIONS = Column.create("nb_publications", TABLE);
	public static final Column COL_NB_COMMENTS = Column.create("nb_comments", TABLE);
	public static final Column COL_NB_RATES = Column.create("nb_rates", TABLE);
	
}
