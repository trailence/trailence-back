package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.trailence.test.AbstractTest;
import org.trailence.trail.dto.Photo;
import org.trailence.trail.dto.Tag;
import org.trailence.trail.dto.TrailCollection;
import org.trailence.trail.dto.TrailCollectionType;

class TestSharedCollections extends AbstractTest {

	@Test
	void collections() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var user3 = test.createUserAndLogin();
		var user4Email = test.email();
		
		// no one invited yet
		var col1 = user1.createSharedCollection();
		// user2 and user3
		var col2 = user1.createSharedCollection(user2.getEmail(), user3.getEmail());
		assertThat(assertMailSent("trailence@trailence.org", user2.getEmail()).getT1()).isEqualTo(user1.getEmail().toLowerCase() + " shared trails with you on trailence.org");
		assertThat(assertMailSent("trailence@trailence.org", user3.getEmail()).getT1()).isEqualTo(user1.getEmail().toLowerCase() + " shared trails with you on trailence.org");
		// user2 and user4
		var col3 = user3.createSharedCollection(user2.getEmail(), user4Email);
		assertThat(assertMailSent("trailence@trailence.org", user2.getEmail()).getT1()).isEqualTo(user3.getEmail().toLowerCase() + " shared trails with you on trailence.org");
		
		var user1Collections = user1.getCollections();
		assertThat(user1Collections).hasSize(3);
		assertThat(user1Collections).filteredOn(col -> TrailCollectionType.MY_TRAILS.equals(col.getType())).singleElement();
		assertThat(user1Collections)
			.filteredOn(col -> TrailCollectionType.SHARED.equals(col.getType()))
			.hasSize(2)
			.satisfies(shared -> {
				assertThat(shared).filteredOn(col -> col1.getName().equals(col.getName())).singleElement()
				.satisfies(col -> {
					assertThat(col.getSharedBy()).isEqualTo(user1.getEmail().toLowerCase());
					assertThat(col.getSharedWith()).isNotNull().isEmpty();
				});
				assertThat(shared).filteredOn(col -> col2.getName().equals(col.getName())).singleElement()
				.satisfies(col -> {
					assertThat(col.getSharedBy()).isEqualTo(user1.getEmail().toLowerCase());
					assertThat(col.getSharedWith()).hasSize(2).containsExactlyInAnyOrder(user2.getEmail().toLowerCase(), user3.getEmail().toLowerCase());
				});
			});
		assertThat(test.getQuotas(user1)).extracting(q -> q.getCollectionsUsed()).isEqualTo((short) 3);

		var user2Collections = user2.getCollections();
		assertThat(user2Collections).hasSize(3);
		assertThat(user2Collections).filteredOn(col -> TrailCollectionType.MY_TRAILS.equals(col.getType())).singleElement();
		assertThat(user2Collections)
			.filteredOn(col -> TrailCollectionType.SHARED.equals(col.getType()))
			.hasSize(2)
			.satisfies(shared -> {
				assertThat(shared).filteredOn(col -> col2.getName().equals(col.getName())).singleElement()
				.satisfies(col -> {
					assertThat(col.getSharedBy()).isEqualTo(user1.getEmail().toLowerCase());
					assertThat(col.getSharedWith()).isNull();
					assertThat(col.getUuid()).isNotEqualTo(col2.getUuid());
				});
				assertThat(shared).filteredOn(col -> col3.getName().equals(col.getName())).singleElement()
				.satisfies(col -> {
					assertThat(col.getSharedBy()).isEqualTo(user3.getEmail().toLowerCase());
					assertThat(col.getSharedWith()).isNull();
					assertThat(col.getUuid()).isNotEqualTo(col3.getUuid());
				});
			});
		assertThat(test.getQuotas(user2)).extracting(q -> q.getCollectionsUsed()).isEqualTo((short) 1);
		
		// but using an old client hides shared collections
		user2.usingOldClient();
		assertThat(user2.getCollections()).singleElement().extracting("type").isEqualTo(TrailCollectionType.MY_TRAILS);
		user2.usingRecentClient();

		var user3Collections = user3.getCollections();
		assertThat(user3Collections).hasSize(3);
		assertThat(user3Collections).filteredOn(col -> TrailCollectionType.MY_TRAILS.equals(col.getType())).singleElement();
		assertThat(user3Collections)
			.filteredOn(col -> TrailCollectionType.SHARED.equals(col.getType()))
			.hasSize(2)
			.satisfies(shared -> {
				assertThat(shared).filteredOn(col -> col2.getName().equals(col.getName())).singleElement()
				.satisfies(col -> {
					assertThat(col.getSharedBy()).isEqualTo(user1.getEmail().toLowerCase());
					assertThat(col.getSharedWith()).isNull();
					assertThat(col.getUuid()).isNotEqualTo(col2.getUuid());
				});
				assertThat(shared).filteredOn(col -> col3.getName().equals(col.getName())).singleElement()
				.satisfies(col -> {
					assertThat(col.getSharedBy()).isEqualTo(user3.getEmail().toLowerCase());
					assertThat(col.getSharedWith()).isNotNull().containsExactlyInAnyOrder(user2.getEmail().toLowerCase(), user4Email.toLowerCase());
				});
			});
		assertThat(test.getQuotas(user3)).extracting(q -> q.getCollectionsUsed()).isEqualTo((short) 2);
		
		var user4 = loginWithShareLink(user3.getEmail(), user4Email);
		var user4Collections = user4.getCollections();
		assertThat(user4Collections).hasSize(2);
		assertThat(user4Collections).filteredOn(col -> TrailCollectionType.MY_TRAILS.equals(col.getType())).singleElement();
		assertThat(user4Collections)
			.filteredOn(col -> TrailCollectionType.SHARED.equals(col.getType()))
			.singleElement()
			.satisfies(col -> {
				assertThat(col.getName()).isEqualTo(col3.getName());
				assertThat(col.getSharedBy()).isEqualTo(user3.getEmail().toLowerCase());
				assertThat(col.getSharedWith()).isNull();
				assertThat(col.getUuid()).isNotEqualTo(col3.getUuid());
			});
		assertThat(test.getQuotas(user4)).extracting(q -> q.getCollectionsUsed()).isEqualTo((short) 1);
		
		// user1 rename col2, but this name is not visible to user2
		var user2Col2 = getSharedCollection(user2Collections, col2.getName());
		col2.setName("updated");
		user1.updateCollections(col2);
		assertThat(user1.getCollections()).filteredOn(col -> col.getUuid().equals(col2.getUuid())).singleElement().extracting("name").isEqualTo("updated");
		assertThat(user2.getCollections()).filteredOn(col -> col.getUuid().equals(user2Col2.getUuid())).singleElement().extracting("name").isEqualTo(user2Col2.getName());
		
		// user2 can rename also for himself
		user2Col2.setName("updated2");
		user2.updateCollections(user2Col2);
		assertThat(user1.getCollections()).filteredOn(col -> col.getUuid().equals(col2.getUuid())).singleElement().extracting("name").isEqualTo("updated");
		assertThat(user2.getCollections()).filteredOn(col -> col.getUuid().equals(user2Col2.getUuid())).singleElement().extracting("name").isEqualTo("updated2");
		
		// user1 invites user4 to col1
		col1.setName("hello user4");
		col1.setSharedWith(List.of(user4Email));
		user1.updateCollections(col1);
		user1Collections = user1.getCollections();
		assertThat(user1Collections).filteredOn(col -> col.getName().equals("hello user4")).singleElement().satisfies(col -> assertThat(col.getSharedWith()).singleElement().isEqualTo(user4Email.toLowerCase()));
		user4Collections = user4.getCollections();
		assertThat(user4Collections).filteredOn(col -> col.getName().equals("hello user4")).singleElement().satisfies(col -> assertThat(col.getSharedBy()).isEqualTo(user1.getEmail().toLowerCase()));
		
		// user4 removes itself
		user4.deleteCollections(getSharedCollection(user4Collections, "hello user4"));
		user1Collections = user1.getCollections();
		assertThat(user1Collections).filteredOn(col -> col.getName().equals("hello user4")).singleElement().satisfies(col -> assertThat(col.getSharedWith()).isEmpty());
		user4Collections = user4.getCollections();
		assertThat(user4Collections).filteredOn(col -> col.getName().equals("hello user4")).isEmpty();
		
		// user1 invites user4 again
		var c = getSharedCollection(user1Collections, "hello user4");
		c.setSharedWith(List.of(user4Email));
		user1.updateCollections(c);
		user1Collections = user1.getCollections();
		assertThat(user1Collections).filteredOn(col -> col.getName().equals("hello user4")).singleElement().satisfies(col -> assertThat(col.getSharedWith()).singleElement().isEqualTo(user4Email.toLowerCase()));
		user4Collections = user4.getCollections();
		assertThat(user4Collections).filteredOn(col -> col.getName().equals("hello user4")).singleElement().satisfies(col -> assertThat(col.getSharedBy()).isEqualTo(user1.getEmail().toLowerCase()));
		
		// user1 removes user4
		c = getSharedCollection(user1Collections, "hello user4");
		c.setSharedWith(List.of());
		user1.updateCollections(c);
		user1Collections = user1.getCollections();
		assertThat(user1Collections).filteredOn(col -> col.getName().equals("hello user4")).singleElement().satisfies(col -> assertThat(col.getSharedWith()).isEmpty());
		user4Collections = user4.getCollections();
		assertThat(user4Collections).filteredOn(col -> col.getName().equals("hello user4")).isEmpty();
		
		// when user3 deletes its account
		deleteMe(user3);
		// then user2 and user4 have no more access to col3
		assertThat(user2.getCollections())
		.hasSize(2)
		.satisfies(collections -> {
			assertThat(collections).filteredOn(col -> TrailCollectionType.MY_TRAILS.equals(col.getType())).singleElement();
			assertThat(collections).filteredOn(col -> TrailCollectionType.SHARED.equals(col.getType())).singleElement()
			.satisfies(col -> {
				assertThat(col.getSharedBy()).isEqualTo(user1.getEmail().toLowerCase());
			});
		});
		assertThat(user4.getCollections()).filteredOn(col -> TrailCollectionType.SHARED.equals(col.getType()) && user3.getEmail().toLowerCase().equals(col.getSharedBy())).isEmpty();
	}
	
	@Test
	void shareWithSameUserSeveralTimesInviteItOnlyOnce() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var user3 = test.createUserAndLogin();
		
		user1.createSharedCollection(user2.getEmail(), user3.getEmail(), user2.getEmail(), user3.getEmail().toUpperCase(), user3.getEmail().toLowerCase(), user1.getEmail().toUpperCase());
		
		var col = assertThat(user1.getCollections())
			.hasSize(2)
			.filteredOn(c -> TrailCollectionType.SHARED.equals(c.getType()))
			.singleElement()
			.satisfies(c -> assertThat(c.getSharedWith()).containsExactlyInAnyOrder(user2.getEmail().toLowerCase(), user3.getEmail().toLowerCase()))
			.actual();
		
		assertThat(user1.createCollections(new TrailCollection[] { col })).singleElement().isEqualTo(col);
		assertThat(user1.getCollections()).hasSize(2);
	}
	
	private static TrailCollection getSharedCollection(List<TrailCollection> collections, String name) {
		return collections.stream().filter(c -> name.equals(c.getName())).findAny().get();
	}
	
	@Test
	void trailsAndTracks() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var colUser1 = user1.createSharedCollection(user2.getEmail());
		var colUser2 = getSharedCollection(user2.getCollections(), colUser1.getName());
		assertThat(colUser1.getUuid()).isNotEqualTo(colUser2.getUuid());
		assertThat(colUser1.getName()).isEqualTo(colUser2.getName());
		var trail1 = user1.createTrail(colUser1, false);
		var trail2 = user2.createTrail(colUser2, true);
		
		assertThat(user1.getTrails())
			.hasSize(2)
			.allSatisfy(trail -> {
				assertThat(trail.getCollectionUuid()).isEqualTo(colUser1.getUuid());
				assertThat(trail.getOwner()).isEqualTo(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser1.getUuid());
			})
			.anySatisfy(trail -> assertThat(trail.getName()).isEqualTo(trail1.getName()))
			.anySatisfy(trail -> assertThat(trail.getName()).isEqualTo(trail2.getName()))
			;
		
		assertThat(user2.getTrails())
			.hasSize(2)
			.allSatisfy(trail -> {
				assertThat(trail.getCollectionUuid()).isEqualTo(colUser2.getUuid());
				assertThat(trail.getOwner()).isEqualTo(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser2.getUuid());
			})
			.anySatisfy(trail -> assertThat(trail.getName()).isEqualTo(trail1.getName()))
			.anySatisfy(trail -> assertThat(trail.getName()).isEqualTo(trail2.getName()))
			;
		
		assertThat(user1.getTracks())
			.hasSize(3)
			.allSatisfy(track -> {
				assertThat(track.getOwner()).isEqualTo(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser1.getUuid());
			});
		
		assertThat(user2.getTracks())
		.hasSize(3)
		.allSatisfy(track -> {
			assertThat(track.getOwner()).isEqualTo(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser2.getUuid());
		});
		
		// using old client hides shared trails
		user2.usingOldClient();
		assertThat(user2.getTrails()).isEmpty();
		assertThat(user2.getTracks()).isEmpty();
		user2.usingRecentClient();
		
		// create and delete tracks
		var trackWithoutTrail = user2.createTrack(colUser2);
		assertThat(user1.getTracks()).hasSize(4).anyMatch(t -> t.getOwner().equals(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser1.getUuid()) && t.getUuid().equals(trackWithoutTrail.getUuid()));
		assertThat(user2.getTracks()).hasSize(4).anyMatch(t -> t.getOwner().equals(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser2.getUuid()) && t.getUuid().equals(trackWithoutTrail.getUuid()));
		assertThat(user1.getTrack(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser1.getUuid(), trackWithoutTrail.getUuid()).getS()).containsExactly(trackWithoutTrail.getS());
		assertThat(user2.getTrack(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser2.getUuid(), trackWithoutTrail.getUuid()).getS()).containsExactly(trackWithoutTrail.getS());
		user2.deleteTracks(trackWithoutTrail);
		assertThat(user1.getTracks()).hasSize(3);
		assertThat(user2.getTracks()).hasSize(3);
		
		// user2 deletes trail1
		var trail1ForUser2 = user2.getTrails().stream().filter(t -> t.getName().equals(trail1.getName())).findAny().get();
		user2.deleteTrails(trail1ForUser2);

		assertThat(user1.getTrails())
			.singleElement()
			.satisfies(trail -> {
				assertThat(trail.getCollectionUuid()).isEqualTo(colUser1.getUuid());
				assertThat(trail.getOwner()).isEqualTo(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser1.getUuid());
				 assertThat(trail.getName()).isEqualTo(trail2.getName());
			});
		
		assertThat(user2.getTrails())
			.singleElement()
			.satisfies(trail -> {
				assertThat(trail.getCollectionUuid()).isEqualTo(colUser2.getUuid());
				assertThat(trail.getOwner()).isEqualTo(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser2.getUuid());
				assertThat(trail.getName()).isEqualTo(trail2.getName());
			});
		
		assertThat(user1.getTracks()).hasSize(1);
		assertThat(user2.getTracks()).hasSize(1);
		
		// user 1 create trail 3
		var trail3 = user1.createTrail(colUser1, false);
		var trail3Name = trail3.getName();
		// user 2 update it
		var trail3ForUser2 = user2.getTrails().stream().filter(t -> t.getName().equals(trail3Name)).findAny().get();
		trail3ForUser2.setDescription("updated");
		trail3ForUser2.setLocation("test");
		user2.updateTrails(trail3ForUser2);
		// user 1 update it again
		trail3 = user1.getTrails().stream().filter(t -> t.getName().equals(trail3Name)).findAny().get();
		assertThat(trail3.getDescription()).isEqualTo("updated");
		assertThat(trail3.getLocation()).isEqualTo("test");
		trail3.setDate(System.currentTimeMillis());
		trail3.setSourceUrl("hello world");
		user1.updateTrails(trail3);
		trail3ForUser2 = user2.getTrails().stream().filter(t -> t.getName().equals(trail3Name)).findAny().get();
		assertThat(trail3ForUser2.getDate()).isEqualTo(trail3.getDate());
		assertThat(trail3ForUser2.getSourceUrl()).isEqualTo("hello world");
		
		// finally user1 deletes the collection
		user1.deleteCollections(colUser1);
		assertThat(user2.getTrails()).isEmpty();
		assertThat(user2.getTracks()).isEmpty();
		assertThat(user2.getCollections()).hasSize(1);
	}
	
	@Test
	void tags() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var colUser1 = user1.createSharedCollection(user2.getEmail());
		var colUser2 = getSharedCollection(user2.getCollections(), colUser1.getName());
		
		var tag1 = user1.createTag(colUser1, null);
		var tag2 = user2.createTag(colUser2, null);
		
		assertThat(user1.getTags())
		.hasSize(2)
		.allSatisfy(tag -> {
			assertThat(tag.getCollectionUuid()).isEqualTo(colUser1.getUuid());
			assertThat(tag.getOwner()).isEqualTo(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser1.getUuid());
		})
		.anySatisfy(tag -> assertThat(tag.getName()).isEqualTo(tag1.getName()))
		.anySatisfy(tag -> assertThat(tag.getName()).isEqualTo(tag2.getName()))
		;
		
		assertThat(user2.getTags())
		.hasSize(2)
		.allSatisfy(tag -> {
			assertThat(tag.getCollectionUuid()).isEqualTo(colUser2.getUuid());
			assertThat(tag.getOwner()).isEqualTo(SharedCollectionUtils.SHARED_OWNER_PREFIX + colUser2.getUuid());
		})
		.anySatisfy(tag -> assertThat(tag.getName()).isEqualTo(tag1.getName()))
		.anySatisfy(tag -> assertThat(tag.getName()).isEqualTo(tag2.getName()))
		;
		
		var trail1 = user1.createTrail(colUser1, false);
		var trail2 = user2.createTrail(colUser2, true);

		var trail2ForUser1 = user1.getTrails().stream().filter(t -> t.getName().equals(trail2.getName())).findAny().get();
		user1.createTrailTag(trail2ForUser1, tag1);
		var tag2ForUser1 = user1.getTags().stream().filter(t -> t.getName().equals(tag2.getName())).findAny().get();
		user1.createTrailTag(trail2ForUser1, tag2ForUser1);
		
		assertThat(user1.getTrailTags())
			.hasSize(2)
			.anyMatch(t -> t.getTrailUuid().equals(trail2ForUser1.getUuid()) && t.getTagUuid().equals(tag1.getUuid()))
			.anyMatch(t -> t.getTrailUuid().equals(trail2ForUser1.getUuid()) && t.getTagUuid().equals(tag2ForUser1.getUuid()))
			.allMatch(t -> t.getOwner().equals(trail1.getOwner()))
			;
		
		var tag1ForUser2 = user2.getTags().stream().filter(t -> t.getName().equals(tag1.getName())).findAny().get();
		assertThat(user2.getTrailTags())
			.hasSize(2)
			.anyMatch(t -> t.getTrailUuid().equals(trail2.getUuid()) && t.getTagUuid().equals(tag1ForUser2.getUuid()))
			.anyMatch(t -> t.getTrailUuid().equals(trail2.getUuid()) && t.getTagUuid().equals(tag2.getUuid()))
			.allMatch(t -> t.getOwner().equals(trail2.getOwner()))
			;
		
		// using old client hides shared trails, tracks, and tags
		user2.usingOldClient();
		assertThat(user2.getTrails()).isEmpty();
		assertThat(user2.getTracks()).isEmpty();
		assertThat(user2.getTags()).isEmpty();
		assertThat(user2.getTrailTags()).isEmpty();
		user2.usingRecentClient();

		// delete trail tags
		user2.deleteTrailTags(user2.getTrailTags().stream().filter(t -> t.getTagUuid().equals(tag1ForUser2.getUuid())).findAny().get());
		assertThat(user1.getTrailTags())
			.singleElement()
			.satisfies(t -> {
				assertThat(t.getTrailUuid()).isEqualTo(trail2ForUser1.getUuid());
				assertThat(t.getTagUuid()).isEqualTo(tag2ForUser1.getUuid());
			});
		
		// update tag
		tag1ForUser2.setName("updated");
		user2.updateTags(tag1ForUser2);
		assertThat(user1.getTags())
			.hasSize(2)
			.anyMatch(tag -> tag.getName().equals("updated"));
		
		// delete tag
		user2.deleteTags(tag1ForUser2);
		assertThat(user1.getTags()).singleElement().extracting(Tag::getName).isEqualTo(tag2.getName());
	}
	
	@Test
	void photos() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var colUser1 = user1.createSharedCollection(user2.getEmail());
		var colUser2 = getSharedCollection(user2.getCollections(), colUser1.getName());
		
		var trail1User1 = user1.createTrail(colUser1, true);
		var trail1User2 = user2.getTrails().stream().filter(t -> t.getName().equals(trail1User1.getName())).findAny().get();
		var trail2User2 = user2.createTrail(colUser2, true);
		var trail2User1 = user1.getTrails().stream().filter(t -> t.getName().equals(trail2User2.getName())).findAny().get();
		
		var photo1Trail1User1 = user1.createPhoto(trail1User1);
		var photo1Trail2User2 = user2.createPhoto(trail2User2);
		
		var photos = user1.getPhotos();
		assertThat(photos)
			.hasSize(2)
			.anyMatch(photo -> photo.getTrailUuid().equals(trail1User1.getUuid()) && photo.getOwner().equals(trail1User1.getOwner()) && photo.getUuid().equals(photo1Trail1User1.getT1().getUuid()))
			.anyMatch(photo -> photo.getTrailUuid().equals(trail2User1.getUuid()) && photo.getOwner().equals(trail2User1.getOwner()) && photo.getUuid().equals(photo1Trail2User2.getT1().getUuid()));
		var photo1Trail2User1 = photos.stream().filter(photo -> photo.getTrailUuid().equals(trail2User1.getUuid()) && photo.getOwner().equals(trail2User1.getOwner()) && photo.getUuid().equals(photo1Trail2User2.getT1().getUuid())).findAny().get();

		photos = user2.getPhotos();
		assertThat(photos)
			.hasSize(2)
			.anyMatch(photo -> photo.getTrailUuid().equals(trail1User2.getUuid()) && photo.getOwner().equals(trail1User2.getOwner()) && photo.getUuid().equals(photo1Trail1User1.getT1().getUuid()))
			.anyMatch(photo -> photo.getTrailUuid().equals(trail2User2.getUuid()) && photo.getOwner().equals(trail2User2.getOwner()) && photo.getUuid().equals(photo1Trail2User2.getT1().getUuid()));
		
		// using old client, shared photos are not returned
		user2.usingOldClient();
		assertThat(user2.getPhotos()).isEmpty();
		user2.usingRecentClient();
		
		// user2 can create photo on trail1
		var photo2Trail1User2 = user2.createPhoto(trail1User2);
		
		photos = user1.getPhotos();
		assertThat(photos)
			.hasSize(3)
			.anyMatch(photo -> photo.getTrailUuid().equals(trail1User1.getUuid()) && photo.getOwner().equals(trail1User1.getOwner()) && photo.getUuid().equals(photo1Trail1User1.getT1().getUuid()))
			.anyMatch(photo -> photo.getTrailUuid().equals(trail2User1.getUuid()) && photo.getOwner().equals(trail2User1.getOwner()) && photo.getUuid().equals(photo1Trail2User2.getT1().getUuid()))
			.anyMatch(photo -> photo.getTrailUuid().equals(trail1User1.getUuid()) && photo.getOwner().equals(trail1User1.getOwner()) && photo.getUuid().equals(photo2Trail1User2.getT1().getUuid()));
		
		photos = user2.getPhotos();
		assertThat(photos)
			.hasSize(3)
			.anyMatch(photo -> photo.getTrailUuid().equals(trail1User2.getUuid()) && photo.getOwner().equals(trail1User2.getOwner()) && photo.getUuid().equals(photo1Trail1User1.getT1().getUuid()))
			.anyMatch(photo -> photo.getTrailUuid().equals(trail2User2.getUuid()) && photo.getOwner().equals(trail2User2.getOwner()) && photo.getUuid().equals(photo1Trail2User2.getT1().getUuid()))
			.anyMatch(photo -> photo.getTrailUuid().equals(trail1User2.getUuid()) && photo.getOwner().equals(trail1User2.getOwner()) && photo.getUuid().equals(photo2Trail1User2.getT1().getUuid()));
		
		// user1 update photo1Trail2
		photo1Trail2User1.setCover(true);
		photo1Trail2User1 = user1.updatePhotos(photo1Trail2User1).getFirst();
		// user2 sees the update
		assertThat(user2.getPhotos()).filteredOn(p -> p.getUuid().equals(photo1Trail2User2.getT1().getUuid())).singleElement().extracting(Photo::isCover).isEqualTo(true);
		
		// user1 delete photo on trail2
		assertThat(user1.deletePhotos(photo1Trail2User1)).isEqualTo((long) photo1Trail2User2.getT2().length);
		assertThat(user1.getPhotos()).hasSize(2);
		assertThat(user2.getPhotos()).hasSize(2);
		
		// user2 delete trail1
		user2.deleteTrails(trail1User2);
		
		assertThat(user1.getPhotos()).isEmpty();
		assertThat(user2.getPhotos()).isEmpty();
	}
	
	@Test
	void testPublicLink() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var user3 = test.createUserAndLogin();
		var colUser1 = user1.createSharedCollection(user2.getEmail());
		
		var trail1User1 = user1.createTrail(colUser1, true);
		var trail1User2 = user2.getTrails().stream().filter(t -> t.getName().equals(trail1User1.getName())).findAny().get();
		
		var linkUser2 = user2.createPublicLink(trail1User2);
		assertThat(user2.getPublicLinkContent(linkUser2).getTrail().getDescription()).isEqualTo(trail1User2.getDescription());
		assertThat(user1.getPublicLinks()).singleElement().satisfies(l -> {
			assertThat(l.getTrailOwner()).isEqualTo(trail1User1.getOwner());
			assertThat(l.getTrailUuid()).isEqualTo(trail1User1.getUuid());
		});
		assertThat(user2.getPublicLinks()).singleElement().satisfies(l -> {
			assertThat(l.getTrailOwner()).isEqualTo(trail1User2.getOwner());
			assertThat(l.getTrailUuid()).isEqualTo(trail1User2.getUuid());
		});
		var linkUser1 = user1.getPublicLinks().getFirst();
		assertThat(user1.getPublicLinkContent(linkUser1).getTrail().getDescription()).isEqualTo(trail1User1.getDescription());
		
		assertThat(user3.getPublicLinkContent(linkUser1).getTrail().getDescription()).isEqualTo(trail1User1.getDescription());
		
		user1.deletePublicLink(linkUser1);
		assertThat(user1.getPublicLinks()).isEmpty();
		assertThat(user2.getPublicLinks()).isEmpty();
	}
	
}
