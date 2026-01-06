package org.trailence.global;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IOUtils {

	public static void readFully(InputStream in, byte[] out) throws IOException {
		int nb = 0;
		int len = out.length;
		while (nb < len) {
			int read = in.read(out, nb, len - nb);
			if (read <= 0) throw new EOFException();
			nb += read;
		}
	}
	
}
