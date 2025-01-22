package org.trailence.quotas.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.sql.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("user_quotas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQuotasEntity {

	@Id
	private String email;
	
	private short collectionsUsed;
	private short collectionsMax;
	private int trailsUsed;
	private int trailsMax;
	private int tracksUsed;
	private int tracksMax;
	private int tracksSizeUsed;
	private int tracksSizeMax;
	private int photosUsed;
	private int photosMax;
	private long photosSizeUsed;
	private long photosSizeMax;
	private int tagsUsed;
	private int tagsMax;
	private int trailTagsUsed;
	private int trailTagsMax;
	private short sharesUsed;
	private short sharesMax;
	
	public static final org.springframework.data.relational.core.sql.Table TABLE = org.springframework.data.relational.core.sql.Table.create("user_quotas");
	public static final Column COL_EMAIL = Column.create("email", TABLE);
	public static final Column COL_COLLECTIONS_USED = Column.create("collections_used", TABLE);
	public static final Column COL_COLLECTIONS_MAX = Column.create("collections_max", TABLE);
	public static final Column COL_TRAILS_USED = Column.create("trails_used", TABLE);
	public static final Column COL_TRAILS_MAX = Column.create("trails_max", TABLE);
	public static final Column COL_TRACKS_USED = Column.create("tracks_used", TABLE);
	public static final Column COL_TRACKS_MAX = Column.create("tracks_max", TABLE);
	public static final Column COL_TRACKS_SIZE_USED = Column.create("tracks_size_used", TABLE);
	public static final Column COL_TRACKS_SIZE_MAX = Column.create("tracks_size_max", TABLE);
	public static final Column COL_PHOTOS_USED = Column.create("photos_used", TABLE);
	public static final Column COL_PHOTOS_MAX = Column.create("photos_max", TABLE);
	public static final Column COL_PHOTOS_SIZE_USED = Column.create("photos_size_used", TABLE);
	public static final Column COL_PHOTOS_SIZE_MAX = Column.create("photos_size_max", TABLE);
	public static final Column COL_TAGS_USED = Column.create("tags_used", TABLE);
	public static final Column COL_TAGS_MAX = Column.create("tags_max", TABLE);
	public static final Column COL_TRAIL_TAGS_USED = Column.create("trail_tags_used", TABLE);
	public static final Column COL_TRAIL_TAGS_MAX = Column.create("trail_tags_max", TABLE);
	public static final Column COL_SHARES_USED = Column.create("shares_used", TABLE);
	public static final Column COL_SHARES_MAX = Column.create("shares_max", TABLE);
}
