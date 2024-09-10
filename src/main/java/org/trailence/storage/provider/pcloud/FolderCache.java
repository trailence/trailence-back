package org.trailence.storage.provider.pcloud;

import java.util.Map;

import reactor.core.publisher.Mono;

class FolderCache {

	final long id;
	Mono<Map<String, Mono<FolderCache>>> subFolders = null;
	
	FolderCache(long id) {
		this.id = id;
	}
	
}
