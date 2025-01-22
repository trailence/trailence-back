package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.trail.dto.Photo;

import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;

class TestPhotos extends AbstractTest {

	@Test
	void crud() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);
		
		var uuid = UUID.randomUUID().toString();
		var content = new byte[123456];
		new Random().nextBytes(content);
		var response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test")
			.body(content)
			.post("/api/photo/v1/" + trail.getUuid() + "/" + uuid);
		assertThat(response.statusCode()).isEqualTo(200);
		var photo = response.getBody().as(Photo.class);
		assertThat(photo.getUuid()).isEqualTo(uuid);
		assertThat(photo.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(photo.getVersion()).isEqualTo(1);
		assertThat(photo.getDescription()).isEqualTo("test");
		assertThat(photo.getTrailUuid()).isEqualTo(trail.getUuid());
		assertThat(photo.getLatitude()).isNull();
		assertThat(photo.getLongitude()).isNull();
		assertThat(photo.getIndex()).isEqualTo(1);
		assertThat(photo.getDateTaken()).isNull();
		
		response = user.post("/api/photo/v1/_bulkGetUpdates", List.of());
		assertThat(response.statusCode()).isEqualTo(200);
		var updates = response.getBody().as(new TypeRef<UpdateResponse<Photo>>() {});
		assertThat(updates.getDeleted()).isEmpty();
		assertThat(updates.getUpdated()).isEmpty();
		assertThat(updates.getCreated()).singleElement().isEqualTo(photo);
		
		response = user.get("/api/photo/v1/" + user.getEmail() + "/" + uuid);
		assertThat(response.statusCode()).isEqualTo(200);
		var download = response.getBody().asByteArray();
		assertThat(download).isEqualTo(content);
		
		photo.setLatitude(125L);
		photo.setLongitude(987L);
		photo.setDescription("updated");
		photo.setIndex(111);
		photo.setDateTaken(123456789L);
		response = user.put("/api/photo/v1/_bulkUpdate", List.of(photo));
		assertThat(response.statusCode()).isEqualTo(200);
		var list = response.getBody().as(Photo[].class);
		assertThat(list).hasSize(1);
		photo = list[0];
		assertThat(photo.getLatitude()).isEqualTo(125);
		assertThat(photo.getLongitude()).isEqualTo(987);
		assertThat(photo.getDescription()).isEqualTo("updated");
		assertThat(photo.getIndex()).isEqualTo(111);
		assertThat(photo.getDateTaken()).isEqualTo(123456789);
		assertThat(photo.getVersion()).isEqualTo(2);
		
		response = user.post("/api/photo/v1/_bulkGetUpdates", List.of());
		assertThat(response.statusCode()).isEqualTo(200);
		updates = response.getBody().as(new TypeRef<UpdateResponse<Photo>>() {});
		assertThat(updates.getDeleted()).isEmpty();
		assertThat(updates.getUpdated()).isEmpty();
		assertThat(updates.getCreated()).singleElement().isEqualTo(photo);

		response = user.post("/api/photo/v1/_bulkDelete", List.of());
		assertThat(response.statusCode()).isEqualTo(200);
		
		response = user.post("/api/photo/v1/_bulkDelete", List.of(photo.getUuid()));
		assertThat(response.statusCode()).isEqualTo(200);

		response = user.post("/api/photo/v1/_bulkGetUpdates", List.of());
		assertThat(response.statusCode()).isEqualTo(200);
		updates = response.getBody().as(new TypeRef<UpdateResponse<Photo>>() {});
		assertThat(updates.getDeleted()).isEmpty();
		assertThat(updates.getUpdated()).isEmpty();
		assertThat(updates.getCreated()).isEmpty();
	}
	
	@Test
	void cannotAccessToPhotoFromAnotherUser() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var mytrails2 = user2.getMyTrails();
		var trail2 = user2.createTrail(mytrails2, true);
		var photo2 = user2.createPhoto(trail2);
		
		// cannot read
		var response = user1.get("/api/photo/v1/" + user2.getEmail() + "/" + photo2.getT1().getUuid());
		TestUtils.expectError(response, 404, "photo-not-found");
		
		// cannot create
		var content = new byte[1234];
		new Random().nextBytes(content);
		response = user1.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test")
			.header("X-DateTaken", "123456789")
			.header("X-Latitude", "147")
			.header("X-Longitude", "369852")
			.header("X-Index", "12")
			.body(content)
			.post("/api/photo/v1/" + trail2.getUuid() + "/" + UUID.randomUUID().toString());
		TestUtils.expectError(response, 404, "trail-not-found");
		
		// cannot update
		var photo = copy(photo2.getT1());
		photo.setDescription("hacked");
		response = user1.put("/api/photo/v1/_bulkUpdate", List.of(photo));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user2.getPhotos()).singleElement().isEqualTo(photo2.getT1());
		
		// cannot delete
		response = user1.post("/api/photo/v1/_bulkDelete", List.of(photo2.getT1().getUuid()));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user2.getPhotos()).singleElement().isEqualTo(photo2.getT1());
	}
	
	@Test
	void createWithInvalidInput() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);
		var content = new byte[10];
		new Random().nextBytes(content);
		
		var response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", RandomStringUtils.randomAlphanumeric(5001))
			.header("X-DateTaken", "123456789")
			.header("X-Latitude", "147")
			.header("X-Longitude", "369852")
			.header("X-Index", "12")
			.body(content)
			.post("/api/photo/v1/" + trail.getUuid() + "/" + UUID.randomUUID().toString());
		TestUtils.expectError(response, 400, "invalid-description-too-long");
		
		response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test")
			.header("X-DateTaken", "abc")
			.header("X-Latitude", "147")
			.header("X-Longitude", "369852")
			.header("X-Index", "12")
			.body(content)
			.post("/api/photo/v1/" + trail.getUuid() + "/" + UUID.randomUUID().toString());
		TestUtils.expectError(response, 400, "invalid-dateTaken");
		
		response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test")
			.header("X-DateTaken", "123456789")
			.header("X-Latitude", "abc")
			.header("X-Longitude", "369852")
			.header("X-Index", "12")
			.body(content)
			.post("/api/photo/v1/" + trail.getUuid() + "/" + UUID.randomUUID().toString());
		TestUtils.expectError(response, 400, "invalid-latitude");
		
		response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test")
			.header("X-DateTaken", "123456789")
			.header("X-Latitude", "147")
			.header("X-Longitude", "abc")
			.header("X-Index", "12")
			.body(content)
			.post("/api/photo/v1/" + trail.getUuid() + "/" + UUID.randomUUID().toString());
		TestUtils.expectError(response, 400, "invalid-longitude");
		
		response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test")
			.header("X-DateTaken", "123456789")
			.header("X-Latitude", "147")
			.header("X-Longitude", "369852")
			.header("X-Index", "abc")
			.body(content)
			.post("/api/photo/v1/" + trail.getUuid() + "/" + UUID.randomUUID().toString());
		TestUtils.expectError(response, 400, "invalid-index");
		
		response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test")
			.header("X-DateTaken", "123456789")
			.header("X-Latitude", "147")
			.header("X-Longitude", "369852")
			.header("X-Index", "12")
			.body(content)
			.post("/api/photo/v1/" + trail.getUuid() + "/" +"1234");
		TestUtils.expectError(response, 400, "invalid-uuid");
		
		response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test")
			.header("X-DateTaken", "123456789")
			.header("X-Latitude", "147")
			.header("X-Longitude", "369852")
			.header("X-Index", "12")
			.body(content)
			.post("/api/photo/v1/" + "1234" + "/" + UUID.randomUUID().toString());
		TestUtils.expectError(response, 400, "invalid-trailUuid");
	}
	
	@Test
	void updateWithInvalidInput() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);
		var photoAndContent = user.createPhoto(trail);
		
		var photo = copy(photoAndContent.getT1());
		photo.setDescription(RandomStringUtils.randomAlphanumeric(5001));
		var response = user.put("/api/photo/v1/_bulkUpdate", List.of(photo));
		TestUtils.expectError(response, 400, "invalid-description-too-long");
		
		photo = copy(photoAndContent.getT1());
		photo.setUuid("1234");
		response = user.put("/api/photo/v1/_bulkUpdate", List.of(photo));
		TestUtils.expectError(response, 400, "invalid-uuid");
		
		photo = copy(photoAndContent.getT1());
		photo.setUuid(null);
		response = user.put("/api/photo/v1/_bulkUpdate", List.of(photo));
		TestUtils.expectError(response, 400, "missing-uuid");
	}
	
	@Test
	void createTwiceTheSameCreateOnlyFirst() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);
		
		var uuid = UUID.randomUUID().toString();
		var content = new byte[123456];
		new Random().nextBytes(content);
		var response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test")
			.body(content)
			.post("/api/photo/v1/" + trail.getUuid() + "/" + uuid);
		assertThat(response.statusCode()).isEqualTo(200);
		var photo1 = response.getBody().as(Photo.class);
		assertThat(photo1.getUuid()).isEqualTo(uuid);
		assertThat(photo1.getDescription()).isEqualTo("test");
		
		response = user.request()
			.contentType(ContentType.BINARY)
			.header("X-Description", "test2")
			.body(content)
			.post("/api/photo/v1/" + trail.getUuid() + "/" + uuid);
		assertThat(response.statusCode()).isEqualTo(200);
		var photo2 = response.getBody().as(Photo.class);
		assertThat(photo2).isEqualTo(photo1);
		
		assertThat(user.getPhotos()).singleElement().isEqualTo(photo1);
	}
	
	@Test
	void updateWithOlderVersionDoNotUpdate() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);
		var photoAndContent = user.createPhoto(trail);
		
		var update1 = copy(photoAndContent.getT1());
		update1.setDescription("update1");
		var response = user.put("/api/photo/v1/_bulkUpdate", List.of(update1));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Photo[].class);
		assertThat(updated).hasSize(1);
		assertThat(updated[0].getDescription()).isEqualTo("update1");
		assertThat(updated[0].getVersion()).isEqualTo(2);

		
		var update2 = copy(update1);
		update2.setDescription("update2");
		update2.setVersion(1);
		response = user.put("/api/photo/v1/_bulkUpdate", List.of(update2));
		assertThat(response.statusCode()).isEqualTo(200);
		updated = response.getBody().as(Photo[].class);
		assertThat(updated).hasSize(1);
		assertThat(updated[0].getDescription()).isEqualTo("update1");
		assertThat(updated[0].getVersion()).isEqualTo(2);
	}
	
	@Test
	void updateWithSameValuesDoNotUpdate() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);
		var photoAndContent = user.createPhoto(trail);
		
		var update1 = copy(photoAndContent.getT1());
		update1.setDescription("update1");
		var response = user.put("/api/photo/v1/_bulkUpdate", List.of(update1));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Photo[].class);
		assertThat(updated).hasSize(1);
		assertThat(updated[0].getDescription()).isEqualTo("update1");
		assertThat(updated[0].getVersion()).isEqualTo(2);
		update1 = updated[0];
		
		var update2 = copy(update1);
		response = user.put("/api/photo/v1/_bulkUpdate", List.of(update2));
		assertThat(response.statusCode()).isEqualTo(200);
		updated = response.getBody().as(Photo[].class);
		assertThat(updated).singleElement().isEqualTo(update1);
	}
	
	@Test
	void testQuotaPhotoNumber() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);
		
		var quotas = user.getAuth().getQuotas();
		quotas.setPhotosMax(5);
		test.asAdmin().updateQuotas(user.getEmail(), quotas);
		
		var photos = new LinkedList<Photo>();
		photos.add(user.createPhoto(trail).getT1());
		photos.add(user.createPhoto(trail).getT1());
		photos.add(user.createPhoto(trail).getT1());
		photos.add(user.createPhoto(trail).getT1());
		assertThat(user.renewToken().getQuotas().getPhotosUsed()).isEqualTo(4);
		photos.add(user.createPhoto(trail).getT1());
		assertThat(user.renewToken().getQuotas().getPhotosUsed()).isEqualTo(5);
		user.createPhoto(trail, 10, 10, 403, "quota-exceeded-photos");
		
		user.deletePhotos(photos.subList(0, 2));
		assertThat(user.renewToken().getQuotas().getPhotosUsed()).isEqualTo(3);
		photos.removeFirst();
		photos.removeFirst();

		photos.add(user.createPhoto(trail).getT1());
		photos.add(user.createPhoto(trail).getT1());
		assertThat(user.renewToken().getQuotas().getPhotosUsed()).isEqualTo(5);
		user.createPhoto(trail, 10, 10, 403, "quota-exceeded-photos");
		
		user.deletePhotos(photos);
		assertThat(user.renewToken().getQuotas().getPhotosUsed()).isZero();
	}
	
	@Test
	void testQuotaPhotoSize() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);
		
		var quotas = user.getAuth().getQuotas();
		quotas.setPhotosSizeMax(10000);
		test.asAdmin().updateQuotas(user.getEmail(), quotas);
		
		var photo1 = user.createPhoto(trail, 9000, 9000, -1, null);
		assertThat(user.renewToken().getQuotas().getPhotosSizeUsed()).isEqualTo(9000);

		user.createPhoto(trail, 2000, 2000, 403, "quota-exceeded-photos-size");
		quotas = user.renewToken().getQuotas();
		assertThat(quotas.getPhotosSizeUsed()).isEqualTo(9000);
		assertThat(quotas.getPhotosUsed()).isEqualTo(1);

		var photo2 = user.createPhoto(trail, 1000, 1000, -1, null);
		quotas = user.renewToken().getQuotas();
		assertThat(quotas.getPhotosSizeUsed()).isEqualTo(10000);
		assertThat(quotas.getPhotosUsed()).isEqualTo(2);

		user.createPhoto(trail, 1, 1, 403, "quota-exceeded-photos-size");
		quotas = user.renewToken().getQuotas();
		assertThat(quotas.getPhotosSizeUsed()).isEqualTo(10000);
		assertThat(quotas.getPhotosUsed()).isEqualTo(2);
		
		long sizeRemoved = user.deletePhotos(photo2.getT1());
		assertThat(sizeRemoved).isEqualTo(1000);
		quotas = user.renewToken().getQuotas();
		assertThat(quotas.getPhotosSizeUsed()).isEqualTo(9000);
		assertThat(quotas.getPhotosUsed()).isEqualTo(1);

		photo2 = user.createPhoto(trail, 1, 1, -1, null);
		quotas = user.renewToken().getQuotas();
		assertThat(quotas.getPhotosSizeUsed()).isEqualTo(9001);
		assertThat(quotas.getPhotosUsed()).isEqualTo(2);
		
		sizeRemoved = user.deletePhotos(photo1.getT1(), photo2.getT1());
		assertThat(sizeRemoved).isEqualTo(9001);
		quotas = user.renewToken().getQuotas();
		assertThat(quotas.getPhotosSizeUsed()).isZero();
		assertThat(quotas.getPhotosUsed()).isZero();
	}
	
	private Photo copy(Photo p) {
		return new Photo(
			p.getUuid(),
			p.getOwner(),
			p.getVersion(),
			p.getCreatedAt(),
			p.getUpdatedAt(),
			p.getTrailUuid(),
			p.getDescription(),
			p.getDateTaken(),
			p.getLatitude(),
			p.getLongitude(),
			p.isCover(),
			p.getIndex()
		);
	}
	
}
