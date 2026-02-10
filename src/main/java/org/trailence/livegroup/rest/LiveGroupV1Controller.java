package org.trailence.livegroup.rest;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.livegroup.LiveGroupService;
import org.trailence.livegroup.dto.LiveGroup;
import org.trailence.livegroup.dto.LiveGroupRequest;
import org.trailence.livegroup.dto.UpdateMyPositionRequest;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/live-group/v1")
@RequiredArgsConstructor
public class LiveGroupV1Controller {

	private final LiveGroupService service;
	
	@GetMapping()
	public Mono<List<LiveGroup>> getGroups(
		@RequestParam(name = "id", required = false) String memberId,
		Authentication auth
	) {
		return service.getGroups(memberId, auth);
	}
	
	@PutMapping()
	public Mono<List<LiveGroup>> updateMyPosition(@RequestBody UpdateMyPositionRequest request, Authentication auth) {
		return service.updateMyPosition(request, auth);
	}
	
	@PostMapping()
	public Mono<LiveGroup> createGroup(@RequestBody LiveGroupRequest request, Authentication auth) {
		return service.createGroup(request, auth);
	}
	
	@PutMapping("/{slug}")
	public Mono<LiveGroup> updateGroup(@PathVariable("slug") String slug, @RequestBody LiveGroupRequest request, Authentication auth) {
		return service.updateGroup(slug, request, auth);
	}
	
	@PostMapping("/join/{slug}")
	public Mono<LiveGroup> joinGroup(@PathVariable("slug") String slug, @RequestBody String myName, @RequestParam(name = "id", required = false) String memberId, Authentication auth) {
		return service.joinGroup(slug, myName, memberId, auth);
	}

	@DeleteMapping("/{slug}")
	public Mono<Void> deleteGroup(@PathVariable("slug") String slug, Authentication auth) {
		return service.deleteGroup(slug, auth);
	}
	
	@DeleteMapping("/join/{slug}")
	public Mono<Void> leaveGroup(@PathVariable("slug") String slug, @RequestParam(name = "id", required = false) String memberId, Authentication auth) {
		return service.leaveGroup(slug, memberId, auth);
	}
	
}
