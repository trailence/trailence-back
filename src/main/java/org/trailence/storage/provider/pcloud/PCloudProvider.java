package org.trailence.storage.provider.pcloud;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.codec.binary.Hex;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.storage.StorageProperties;
import org.trailence.storage.StorageProperties.StorageLocationProperties;
import org.trailence.storage.provider.FileStorageLocation;
import org.trailence.storage.provider.FileStorageProvider;
import org.trailence.storage.provider.StorageUtils;
import org.trailence.storage.provider.pcloud.dto.PCloudFileLinkResponse;
import org.trailence.storage.provider.pcloud.dto.PCloudFolderResponse;
import org.trailence.storage.provider.pcloud.dto.PCloudUploadResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("java:S4042")
public class PCloudProvider implements FileStorageProvider {

	private final StorageProperties.StorageProviderProperties properties;
	
	private static final String PROTOCOL = "https://";
	//private static final String PARAM_USERNAME = "username";
	//private static final String PARAM_PASSWORD = "password";
	//private static final String QUERY_AUTH = "?" + PARAM_USERNAME + "={" + PARAM_USERNAME + "}&" + PARAM_PASSWORD + "={" + PARAM_PASSWORD + "}";
	private static final String QUERY_AUTH = "?auth={authToken}";
	private static final String PARAM_AUTH = "authToken";
	private static final String PARAM_FOLDERID = "folderid";
	private static final String QUERY_FOLDERID = "&" + PARAM_FOLDERID + "={" + PARAM_FOLDERID + "}";
	private static final String PARAM_FILEID = "fileid";
	private static final String QUERY_FILEID = "&" + PARAM_FILEID + "={" + PARAM_FILEID + "}";
	private static final String PARAM_PATH = "path";
	private static final String QUERY_PATH = "&" + PARAM_PATH + "={" + PARAM_PATH + "}";
	
	private WebClient getClient() {
		return WebClient.builder().baseUrl(PROTOCOL + properties.getUrl()).build();
	}
	
	private String authToken = null;
	
	@Override
	public Mono<PCloudProvider> init() {
		log.info("Authenticating to PCloud...");
		return (properties.getAuthkey() != null && !properties.getAuthkey().isBlank() ? this.checkAuthKey() : Mono.just(false))
		.flatMap(withKey -> {
			if (withKey.booleanValue()) return Mono.just(this);
			return this.authenticate().thenReturn(this);
		});
	}
	
	private Mono<Boolean> checkAuthKey() {
		return getClient().get().uri("/userinfo?auth={authKey}", Map.of("authKey", properties.getAuthkey()))
		.exchangeToMono(response -> response.bodyToMono(Map.class))
		.map(m -> {
			var email = m.get("email");
			if (properties.getUsername().equals(email)) {
				log.info("PCloud auth key is valid.");
				this.authToken = properties.getAuthkey();
				return true;
			}
			log.error("PCloud auth key is not valid: {}\nExpected email is {}, found is {}", m, properties.getUsername(), email);
			return false;
		});
	}
	
	private Mono<Boolean> authenticate() {
		return getClient().post()
		.uri("/login")
		.contentType(MediaType.APPLICATION_FORM_URLENCODED)
		.body(BodyInserters
			.fromFormData("username", properties.getUsername())
			.with("password", properties.getPassword())
			.with("os", "4")
			.with("osversion", "0.0.0")
			.with("deviceid", "trailence")
		)
		.exchangeToMono(response -> response.bodyToMono(Map.class))
		.map(m -> {
			var auth = m.get("auth");
			if (auth != null) {
				this.authToken = auth.toString();
				log.info("PCloud authentication done.");
				return true;
			}
			log.error("PCloud authentication failed: {}", m);
			return false;
		});
	}
	
	@Override
	public Mono<? extends FileStorageLocation> createLocation(StorageLocationProperties properties) {
		String root = properties.getRoot();
		long rootId;
		String rootPath;
		if (root == null || root.isBlank()) {
			rootId = 0;
			rootPath = "";
		} else {
			var i = root.indexOf(';');
			if (i < 0) {
				rootId = Long.parseLong(root);
				rootPath = null;
			} else {
				rootId = Long.parseLong(root.substring(0, i));
				rootPath = root.substring(i + 1);
			}
		}
		return Mono.just(new Location(rootId, rootPath));
	}
	
	@RequiredArgsConstructor
	private class Location implements FileStorageLocation {
		private final long rootFolderId;
		private final String rootFolderPath;
		private FolderCache root = null;

		@Override
		public Mono<String> storeFile(String path, Flux<DataBuffer> content, long expectedSize) {
			String[] pathElements = path.split("/");
			Mono<Long> folderId = pathElements.length > 1 ? getFolderId(Arrays.copyOfRange(pathElements, 0, pathElements.length - 1)) : Mono.just(rootFolderId);
			String filename = pathElements[pathElements.length - 1];
			return folderId
			.flatMap(folderid ->
				StorageUtils.toTmpFileWithDigests(content, "SHA1", "SHA256")
				.flatMap(fileAndDigests -> {
					log.info("Uploading file {} in folder {}", filename, folderid);
					return getClient().post()
					.uri("/uploadfile" + QUERY_AUTH + QUERY_FOLDERID + "&filename={filename}&nopartial=1", 
						Map.of(PARAM_AUTH, authToken, PARAM_FOLDERID, Long.toString(folderid), "filename", filename)
					)
					.contentType(MediaType.APPLICATION_OCTET_STREAM)
					.body(BodyInserters.fromResource(new FileSystemResource(fileAndDigests.getKey())))
					.exchangeToMono(response -> response.bodyToMono(PCloudUploadResponse.class))
					.doOnNext(_ -> log.info("File uploaded: {} in folder {}", filename, folderid))
					.doOnError(error -> log.warn("Error uploading file {} in folder {}", filename, folderid, error))
					.flatMap(response -> checkUploadResponse(response, fileAndDigests.getValue(), path, expectedSize))
					.doFinally(_ -> fileAndDigests.getKey().delete());
				})
			);
		}
		
		private Mono<String> checkUploadResponse(PCloudUploadResponse response, Map<String, byte[]> expectedDigests, String path, long expectedSize) {
			boolean valid = true;
			String fileId = null;
			try {
				var checksums = response.getChecksums().getFirst();
				valid = valid && Arrays.equals(Hex.decodeHex(checksums.getSha1()), expectedDigests.get("SHA1"));
				valid = valid && Arrays.equals(Hex.decodeHex(checksums.getSha256()), expectedDigests.get("SHA256"));
				var metadata = response.getMetadata().get(0);
				fileId = Long.toString(metadata.getFileid());
				if (metadata.getSize() != null && metadata.getSize().longValue() != expectedSize) valid = false;
			} catch (Exception e) {
				log.error("Error checking uploaded file result", e);
				valid = false;
			}
			if (!valid) {
				if (fileId != null)
					return deleteFile(fileId, path).then(Mono.error(new RuntimeException("Error uploading file")));
				return Mono.error(new RuntimeException("Error uploading file"));
			}
			return Mono.just(fileId);
		}
		
		@Override
		public Flux<DataBuffer> getFile(String fileId, String path, Supplier<NotFoundException> onNotFound) {
			return (fileId != null ? getFileUrl(Long.parseLong(fileId), onNotFound) : getFileUrl(path, onNotFound))
			.flatMapMany(url -> {
				log.info("Downloading file id {} from {}", fileId, path);
				WebClient client = WebClient.builder().build();
				return client.get().uri(url).exchangeToFlux(response -> response.body(BodyExtractors.toDataBuffers()))
				.doOnComplete(() -> log.info("File downloaded: id {} from {}", fileId, path))
				.doOnError(error -> log.warn("Error downloading file id {} from {}", fileId, path, error));
			});
		}
		
		private Mono<String> getFileUrl(Long fileId, Supplier<NotFoundException> onNotFound) {
			return getFileUrl(
				getClient().get()
				.uri("/getfilelink" + QUERY_AUTH + QUERY_FILEID,
					Map.of(PARAM_AUTH, authToken, PARAM_FILEID, Long.toString(fileId))
				),
				fileId.toString(),
				onNotFound
			);
		}
		
		private Mono<String> getFileUrl(String path, Supplier<NotFoundException> onNotFound) {
			return getFileUrl(
				getClient().get()
				.uri("/getfilelink" + QUERY_AUTH + QUERY_PATH,
					Map.of(PARAM_AUTH, authToken, PARAM_PATH, rootFolderPath + '/' + path)
				),
				path,
				onNotFound
			);
		}
		
		private Mono<String> getFileUrl(WebClient.RequestHeadersSpec<?> request, String fileDescr, Supplier<NotFoundException> onNotFound) {
			log.info("Creating download link for file {}", fileDescr);
			return request
			.exchangeToMono(response -> response.bodyToMono(PCloudFileLinkResponse.class))
			.doOnNext(_ -> log.info("Download link created for file {}", fileDescr))
			.doOnError(error -> log.warn("Error creating download link for file {}", fileDescr, error))
			.flatMap(response -> {
				var hosts = response.getHosts();
				if (hosts == null || hosts.isEmpty()) return Mono.error(onNotFound.get());
				return Mono.just(PROTOCOL + hosts.getFirst() + response.getPath());
			});
		}
		
		@Override
		public Mono<Void> deleteFile(String fileId, String path) {
			return Mono.defer(() -> {
				log.info("Deleting file id {} in {}", fileId, path);
				return getClient().get()
				.uri("/deletefile" + QUERY_AUTH + QUERY_FILEID,
					Map.of(PARAM_AUTH, authToken, PARAM_FILEID, fileId)
				)
				.exchangeToMono(_ -> Mono.<Void>empty())
				.doOnSuccess(_ -> log.info("File deleted: {} in {}", fileId, path))
				.doOnError(error -> log.warn("Error deleting file id {} in {}", fileId, path, error));
			});
		}
		
		private Mono<Long> getFolderId(String[] path) {
			return Mono.fromSupplier(() -> {
				synchronized (this) {
					if (root == null) root = new FolderCache(rootFolderId);
				}
				return root;
			})
			.subscribeOn(Schedulers.boundedElastic())
			.flatMap(cache -> getFolderId(path, cache));
		}
		
		@SuppressWarnings("java:S2445")
		private Mono<Long> getFolderId(String[] path, FolderCache cache) {
			return Mono.defer(() -> {
				synchronized (cache) {
					if (cache.subFolders != null) return cache.subFolders;
					cache.subFolders = listSubFolders(cache);
					return cache.subFolders;
				}
			})
			.subscribeOn(Schedulers.boundedElastic())
			.flatMap(subFolders -> {
				synchronized (subFolders) {
					Mono<FolderCache> folder = subFolders.get(path[0]);
					if (folder != null) return folder;
					folder = createFolder(cache.id, path[0]);
					subFolders.put(path[0], folder);
					return folder;
				}
			})
			.flatMap(folder -> {
				if (path.length == 1) return Mono.just(folder.id);
				return getFolderId(Arrays.copyOfRange(path, 1, path.length), folder);
			});
		}
		
		private Mono<Map<String, Mono<FolderCache>>> listSubFolders(FolderCache cache) {
			return getClient().get()
			.uri("/listfolder" + QUERY_AUTH + QUERY_FOLDERID,
				Map.of(PARAM_AUTH, authToken, PARAM_FOLDERID, Long.toString(cache.id))
			)
			.exchangeToMono(response -> response.bodyToMono(PCloudFolderResponse.class))
			.map(response -> {
				Map<String, Mono<FolderCache>> folders = new HashMap<>();
				for (var content : response.getMetadata().getContents()) {
					if (content.isIsfolder()) {
						folders.put(content.getName(), Mono.just(new FolderCache(content.getFolderid())).share());
					}
				}
				return folders;
			})
			.share();
		}
		
		private Mono<FolderCache> createFolder(long parentFolderId, String name) {
			return getClient().get()
			.uri("/createfolder" + QUERY_AUTH + QUERY_FOLDERID + "&name={name}",
				Map.of(PARAM_AUTH, authToken, PARAM_FOLDERID, Long.toString(parentFolderId), "name", name)
			)
			.exchangeToMono(response -> response.bodyToMono(PCloudFolderResponse.class))
			.map(response -> new FolderCache(response.getMetadata().getFolderid()))
			.share();
		}
	}
	
}
