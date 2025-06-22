package org.trailence.trail.dto;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum TrailCollectionType {
    MY_TRAILS,
    CUSTOM,
    PUB_DRAFT,
    PUB_SUBMIT,
    PUB_REJECT;
	
	public static final Set<TrailCollectionType> PUBLICATION_TYPES;
	public static final Set<TrailCollectionType> NOT_IN_QUOTA;
	
	static {
		var types = new HashSet<TrailCollectionType>();
		types.add(PUB_DRAFT);
		types.add(PUB_SUBMIT);
		types.add(PUB_REJECT);
		PUBLICATION_TYPES = Collections.unmodifiableSet(types);
		types = new HashSet<TrailCollectionType>();
		types.addAll(PUBLICATION_TYPES);
		NOT_IN_QUOTA = Collections.unmodifiableSet(types);
	}
	
	public static final String EXCLUDE_NOT_IN_QUOTA_TYPES = "(" + String.join(",",TrailCollectionType.NOT_IN_QUOTA.stream().map(t -> "'" + t.name() + "'").toList()) + ")";

}
