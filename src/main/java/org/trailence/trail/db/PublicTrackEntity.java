package org.trailence.trail.db;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("public_tracks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicTrackEntity {

	@Id
	private UUID trailUuid;
	private byte[] data;

}
