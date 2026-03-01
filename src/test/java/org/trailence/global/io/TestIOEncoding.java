package org.trailence.global.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;

class TestIOEncoding {

	@Test
	void testInteger2() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		IOEncoding.encodeInteger2(0, out);
		IOEncoding.encodeInteger2(10, out);
		IOEncoding.encodeInteger2(0x7F, out);
		IOEncoding.encodeInteger2(0x80, out);
		IOEncoding.encodeInteger2(0x81, out);
		IOEncoding.encodeInteger2(0x1000, out);
		IOEncoding.encodeInteger2(0x7FFE, out);
		IOEncoding.encodeInteger2(0x7FFF, out);
		byte[] bytes = out.toByteArray();
		assertThat(bytes).hasSize(13);
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		assertThat(IOEncoding.decodeInteger2(in)).isZero();
		assertThat(IOEncoding.decodeInteger2(in)).isEqualTo(10);
		assertThat(IOEncoding.decodeInteger2(in)).isEqualTo(0x7F);
		assertThat(IOEncoding.decodeInteger2(in)).isEqualTo(0x80);
		assertThat(IOEncoding.decodeInteger2(in)).isEqualTo(0x81);
		assertThat(IOEncoding.decodeInteger2(in)).isEqualTo(0x1000);
		assertThat(IOEncoding.decodeInteger2(in)).isEqualTo(0x7FFE);
		assertThat(IOEncoding.decodeInteger2(in)).isEqualTo(0x7FFF);
		assertThat(in.available()).isZero();
		
		assertThrows(IOException.class, () -> IOEncoding.encodeInteger2(-1, out));
		assertThrows(IOException.class, () -> IOEncoding.encodeInteger2(0x8000, out));
	}
	
	@Test
	void testInteger2to4() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		IOEncoding.encodeInteger2to4(0, out);
		IOEncoding.encodeInteger2to4(1, out);
		IOEncoding.encodeInteger2to4(0x7FFF, out);
		IOEncoding.encodeInteger2to4(0x8000, out);
		IOEncoding.encodeInteger2to4(0x407FFF, out);
		IOEncoding.encodeInteger2to4(0x408000, out);
		IOEncoding.encodeInteger2to4(0x408001, out);
		IOEncoding.encodeInteger2to4(0x40407FFF, out);
		assertThrows(IOException.class, () -> IOEncoding.encodeInteger2to4(-1, out));
		assertThrows(IOException.class, () -> IOEncoding.encodeInteger2to4(0x40408000, out));
		
		byte[] bytes = out.toByteArray();
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		assertThat(IOEncoding.decodeInteger2to4(in)).isZero();
		assertThat(IOEncoding.decodeInteger2to4(in)).isEqualTo(1);
		assertThat(IOEncoding.decodeInteger2to4(in)).isEqualTo(0x7FFF);
		assertThat(IOEncoding.decodeInteger2to4(in)).isEqualTo(0x8000);
		assertThat(IOEncoding.decodeInteger2to4(in)).isEqualTo(0x407FFF);
		assertThat(IOEncoding.decodeInteger2to4(in)).isEqualTo(0x408000);
		assertThat(IOEncoding.decodeInteger2to4(in)).isEqualTo(0x408001);
		assertThat(IOEncoding.decodeInteger2to4(in)).isEqualTo(0x40407FFF);
		assertThat(in.available()).isZero();
	}
	
	@Test
	void testNumber() throws IOException {
		long max = 0xFFL;
		for (int i = 1; i <= 8; ++i) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			long[] values = new long[1002];
			for (int j = 0; j < 1000; ++j) {
				values[j] = RandomUtils.insecure().randomLong(0, max + 1);
			}
			values[1000] = 0;
			values[1001] = max;
			for (int j = 0; j < values.length; ++j) {
				IOEncoding.encodeNumber(out, values[j], i);
			}
			byte[] bytes = out.toByteArray();
			ByteArrayInputStream in = new ByteArrayInputStream(bytes);
			for (int j = 0; j < values.length; ++j) {
				assertThat(IOEncoding.decodeNumber(in, i)).isEqualTo(values[j]);
			}
			assertThat(in.available()).isZero();
			max = i < 7 ? (max << 8) | 0xFF : 0x7FFFFFFFFFFFFFFEL;
		}
	}
	
	@Test
	void testBits() throws IOException {
		for (int nbBits = 1; nbBits < 1000; ++nbBits) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			boolean[] bits = new boolean[nbBits];
			for (int i = 0; i < nbBits; ++i) bits[i] = RandomUtils.insecure().randomBoolean();
			var encoder = new IOEncoding.BitEncoder(out);
			for (int i = 0; i < nbBits; ++i) encoder.encode(bits[i]);
			encoder.close();
			byte[] bytes = out.toByteArray();
			ByteArrayInputStream in = new ByteArrayInputStream(bytes);
			var decoder = new IOEncoding.BitDecoder(in);
			for (int i = 0; i < nbBits; ++i)
				assertThat(decoder.decode()).isEqualTo(bits[i]);
			assertThat(in.available()).isZero();
		}
	}
	
}
