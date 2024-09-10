package org.trailence.storage.provider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings({"java:S899", "java:S4042"})
public final class StorageUtils {

	public static Mono<Pair<File, Map<String, byte[]>>> toTmpFileWithDigests(Flux<DataBuffer> buffers, String... digests) {
		return Mono.defer(() -> {
			File tmpFile = null;
			OutputStream out;
			try {
				tmpFile = File.createTempFile("upload", "pcloud");
				tmpFile.deleteOnExit();
				out = new FileOutputStream(tmpFile);
			} catch (Exception e) {
				if (tmpFile != null) tmpFile.delete();
				return Mono.error(e);
			}
			File tmp = tmpFile;
			
			Map<String, DigestOutputStream> digestStreams = new HashMap<>();
			
			for (String digest : digests) {
				try {
					DigestOutputStream dout = new DigestOutputStream(out, MessageDigest.getInstance(digest));
					digestStreams.put(digest, dout);
					out = dout;
				} catch (Exception e) {
					// ignore
				}
			}
			
			return writeAndClose(buffers, out)
			.then(Mono.fromSupplier(() -> {
				Map<String, byte[]> digestMap = new HashMap<>();
				for (Map.Entry<String, DigestOutputStream> entry : digestStreams.entrySet()) {
					digestMap.put(entry.getKey(), entry.getValue().getMessageDigest().digest());
				}
				return Pair.of(tmp, digestMap);
			}));
		})
		.subscribeOn(Schedulers.boundedElastic());
	}
	
	public static Mono<Void> writeAndClose(Flux<DataBuffer> buffers, OutputStream out) {
		return DataBufferUtils.write(buffers, out)
		.map(DataBufferUtils::release)
		.doFinally(s -> {
			try {
				out.close();
			} catch (Exception e) {
				// ignore
			}
		}).then();
	}
	
}
