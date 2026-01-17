package org.trailence.global.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.trailence.global.AccessibleByteArrayOutputStream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IOEncoding {

	/**
	 * Encode an integer between 0 and 0x7FFF:<ul>
	 * <li>First byte contains a flag 0x80: if set the value is encoded on 2 bytes, else on 1 byte</li>
	 * </ul>
	 */
	public static void encodeInteger2(int value, OutputStream out) throws IOException {
		if (value < 0 || value > 0x7FFF) throw new IOException("Invalid integer value: " + value);
		if (value <= 0x7F) {
			out.write(value);
			return;
		}
		out.write(((value & 0x7F00) >> 8) | 0x80);
		out.write(value & 0xFF);
	}
	
	public static int decodeInteger2(InputStream in) throws IOException {
		int i1 = in.read();
		if ((i1 & 0x80) == 0) return i1;
		int i2 = in.read();
		return ((i1 & 0x7F) << 8) | i2;
	}
	
	/**
	 * Encode an integer between 0 and 0x40407FFF:<ul>
	 * <li>first bit 0x80: if 0, encoded on 2 bytes (0 to 0x7FFF)</li>
	 * <li>if first bit is set, second bit:<ul>
	 *   <li>0: encoded on 3 bytes: values 0x8000 to 0x407FFF (0x8000 + 0x3FFFFF)</li>
	 *   <li>1: encoded on 4 bytes: values 0x408000 to 0x40407FFF (0x408000 + 0x3FFFFFFF)</li>
	 * </ul></li>
	 * </ul>
	 */
	public static void encodeInteger2to4(int value, OutputStream out) throws IOException {
		if (value < 0 || value > 0x40407FFF) throw new IOException("Invalid integer value: " + value);
		if (value < 0x8000) {
			out.write((value & 0x7F00) >> 8);
			out.write(value & 0xFF);
			return;
		}
		if (value < 0x408000) {
			value -= 0x8000;
			out.write(((value & 0x3F0000) >> 16) | 0x80);
			out.write((value & 0xFF00) >> 8);
			out.write(value & 0xFF);
			return;
		}
		value -= 0x408000;
		out.write(((value & 0x3F000000) >> 24) | 0xC0);
		out.write((value & 0xFF0000) >> 16);
		out.write((value & 0xFF00) >> 8);
		out.write(value & 0xFF);
	}
	
	public static int decodeInteger2to4(InputStream in) throws IOException {
		int i1 = in.read();
		int i2 = in.read();
		if ((i1 & 0x80) == 0) return (i1 << 8) | i2;
		if ((i1 & 0xC0) == 0x80) {
			return (((i1 & 0x3F) << 16) | (i2 << 8) | in.read()) + 0x8000;
		}
		int i3 = in.read();
		int i4 = in.read();
		return (((i1 & 0x3F) << 24) | (i2 << 16) | (i3 << 8) | i4) + 0x408000;
	}
	
	public static void encodeNumber(OutputStream out, long v, int bytes) throws IOException {
		out.write((int)(v & 0xFF));
		if (bytes == 1) return;
		out.write((int)((v & 0xFF00) >> 8));
		if (bytes == 2) return;
		out.write((int)((v & 0xFF0000) >> 16));
		if (bytes == 3) return;
		out.write((int)((v & 0xFF000000) >> 24));
		if (bytes == 4) return;
		out.write((int)((v & 0xFF00000000L) >> 32));
		if (bytes == 5) return;
		out.write((int)((v & 0xFF0000000000L) >> 40));
		if (bytes == 6) return;
		out.write((int)((v & 0xFF000000000000L) >> 48));
		if (bytes == 7) return;
		out.write((int)((v >> 56) & 0xFF));
	}
	
	public static long decodeNumber(InputStream in, int bytes) throws IOException {
		long v = in.read();
		if (bytes == 1) return v;
		v |= in.read() << 8;
		if (bytes == 2) return v;
		v |= in.read() << 16;
		if (bytes == 3) return v;
		v |= ((long)in.read()) << 24;
		if (bytes == 4) return v;
		v |= ((long)in.read()) << 32;
		if (bytes == 5) return v;
		v |= ((long)in.read()) << 40;
		if (bytes == 6) return v;
		v |= ((long)in.read()) << 48;
		if (bytes == 7) return v;
		v |= ((long)in.read()) << 56;
		return v;
	}
	
	public static class BitEncoder {
		private OutputStream out;
		private int currentByte = 0;
		private int currentMask = 1;
		
		public BitEncoder(OutputStream out) {
			this.out = out;
		}
		
		public void encode(boolean bit) throws IOException {
			if (bit) currentByte |= currentMask;
			currentMask <<= 1;
			if (currentMask == 0x100) {
				out.write(currentByte);
				currentByte = 0;
				currentMask = 1;
			}
		}
		
		public void encodeNumber(int number, int nbBits) throws IOException {
			for (int i = 0; i < nbBits; ++i) {
				encode(((number >> i) & 1) != 0);
			}
		}
		
		public void close() throws IOException {
			if (currentMask > 1) {
				out.write(currentByte);
			}
		}
	}
	
	public static class BitDecoder {
		private InputStream in;
		private int currentMask = 0x100;
		private int currentByte = 0;
		
		public BitDecoder(InputStream in) {
			this.in = in;
		}
		
		public boolean decode() throws IOException {
			if (currentMask == 0x100) {
				currentByte = in.read();
				currentMask = 1;
			}
			boolean bit = (currentByte & currentMask) != 0;
			currentMask <<= 1;
			return bit;
		}
		
		public int decodeNumber(int nbBits) throws IOException {
			int n = 0;
			for (int i = 0; i < nbBits; ++i) {
				if (decode())
					n |= 1 << i;
			}
			return n;
		}
	}
	
	public static class DigitEncoder {
		private AccessibleByteArrayOutputStream out = new AccessibleByteArrayOutputStream(1024);
		private long currentValue = 0;
		private long currentMask = 1;
		
		public void encode(int digit) {
			currentValue += (digit - 1) * currentMask;
			if (currentMask == 150094635296999121L) {
				flush();
			} else {
				currentMask *= 9;
			}
		}
		
		public void flush() {
			out.write((int)(currentValue & 0xFF));
			out.write((int)((currentValue & 0xFF00L) >> 8));
			out.write((int)((currentValue & 0xFF0000L) >> 16));
			out.write((int)((currentValue & 0xFF000000L) >> 24));
			out.write((int)((currentValue & 0xFF00000000L) >> 32));
			out.write((int)((currentValue & 0xFF0000000000L) >> 40));
			out.write((int)((currentValue & 0xFF000000000000L) >> 48));
			out.write((int)((currentValue >> 56) & 0xFF));
			currentValue = 0;
			currentMask = 1;
		}
		
		public void close(OutputStream o) throws IOException{
			if (currentMask > 1) flush();
			o.write(out.getData(), 0, out.getLength());
		}
	}
	
	public static class DigitDecoder {
		private InputStream in;
		private long currentValue = 0;
		private long currentMask = 1;
		public DigitDecoder(InputStream in) {
			this.in = in;
		}
		public int decode() throws IOException {
			if (currentMask == 1) {
				currentValue = in.read() |
					(((long)in.read()) << 8) |
					(((long)in.read()) << 16) |
					(((long)in.read()) << 24) |
					(((long)in.read()) << 32) |
					(((long)in.read()) << 40) |
					(((long)in.read()) << 48) |
					(((long)in.read()) << 56);
				currentMask = 9;
				return (int)(currentValue % 9) + 1;
			}
			int v = (int)((currentValue / currentMask) % 9) + 1;
			if (currentMask == 150094635296999121L) {
				currentMask = 1;
			} else {
				currentMask *= 9;
			}
			return v;
		}
	}

	public abstract static class SplitNumberBytesEncoder {
		protected byte[] bytes;
		protected int nbValues;
		
		SplitNumberBytesEncoder(int nbBytes, int nbValues) {
			bytes = new byte[nbValues * nbBytes];
			this.nbValues = nbValues;
		}
		
		public abstract void encode(long v, int vi);
		
		public final void write(OutputStream out) throws IOException {
			out.write(bytes);
		}
	}
	
	public static SplitNumberBytesEncoder getSplitNumberBytesEncoder(int nbBytes, int nbValues) {
		switch (nbBytes) {
		case 1: return new SplitNumberBytesEncoder1(nbValues);
		case 2: return new SplitNumberBytesEncoder2(nbValues);
		case 3: return new SplitNumberBytesEncoder3(nbValues);
		case 4: return new SplitNumberBytesEncoder4(nbValues);
		case 5: return new SplitNumberBytesEncoder5(nbValues);
		case 6: return new SplitNumberBytesEncoder6(nbValues);
		case 7: return new SplitNumberBytesEncoder7(nbValues);
		default: return new SplitNumberBytesEncoder8(nbValues);
		}
	}
	
	private static class SplitNumberBytesEncoder1 extends SplitNumberBytesEncoder {
		private SplitNumberBytesEncoder1(int nbValues) { super(1, nbValues); } 
		
		@Override
		public void encode(long v, int vi) {
			bytes[vi] = (byte)(v & 0xFF);
		}
	}

	private static class SplitNumberBytesEncoder2 extends SplitNumberBytesEncoder {
		private SplitNumberBytesEncoder2(int nbValues) { super(2, nbValues); }
		
		@Override
		public void encode(long v, int vi) {
			bytes[vi] = (byte)(v & 0xFF);
			bytes[nbValues + vi] = (byte)((v & 0xFF00) >> 8);
		}
	}

	private static class SplitNumberBytesEncoder3 extends SplitNumberBytesEncoder {
		private SplitNumberBytesEncoder3(int nbValues) { super(3, nbValues); }		

		@Override
		public void encode(long v, int vi) {
			bytes[vi] = (byte)(v & 0xFF);
			bytes[nbValues + vi] = (byte)((v & 0xFF00) >> 8);
			bytes[2 * nbValues + vi] = (byte)((v & 0xFF0000) >> 16);
		}
	}

	private static class SplitNumberBytesEncoder4 extends SplitNumberBytesEncoder {
		private SplitNumberBytesEncoder4(int nbValues) { super(4, nbValues); }		
		
		@Override
		public void encode(long v, int vi) {
			bytes[vi] = (byte)(v & 0xFF);
			bytes[nbValues + vi] = (byte)((v & 0xFF00) >> 8);
			bytes[2 * nbValues + vi] = (byte)((v & 0xFF0000) >> 16);
			bytes[3 * nbValues + vi] = (byte)((v & 0xFF000000L) >> 24);
		}
	}

	private static class SplitNumberBytesEncoder5 extends SplitNumberBytesEncoder {
		private SplitNumberBytesEncoder5(int nbValues) { super(5, nbValues); }
		
		@Override
		public void encode(long v, int vi) {
			bytes[vi] = (byte)(v & 0xFF);
			bytes[nbValues + vi] = (byte)((v & 0xFF00) >> 8);
			bytes[2 * nbValues + vi] = (byte)((v & 0xFF0000) >> 16);
			bytes[3 * nbValues + vi] = (byte)((v & 0xFF000000L) >> 24);
			bytes[4 * nbValues + vi] = (byte)((v & 0xFF00000000L) >> 32);
		}
	}

	private static class SplitNumberBytesEncoder6 extends SplitNumberBytesEncoder {
		private SplitNumberBytesEncoder6(int nbValues) { super(6, nbValues); }
		
		@Override
		public void encode(long v, int vi) {
			bytes[vi] = (byte)(v & 0xFF);
			bytes[nbValues + vi] = (byte)((v & 0xFF00) >> 8);
			bytes[2 * nbValues + vi] = (byte)((v & 0xFF0000) >> 16);
			bytes[3 * nbValues + vi] = (byte)((v & 0xFF000000L) >> 24);
			bytes[4 * nbValues + vi] = (byte)((v & 0xFF00000000L) >> 32);
			bytes[5 * nbValues + vi] = (byte)((v & 0xFF0000000000L) >> 40);
		}
	}

	private static class SplitNumberBytesEncoder7 extends SplitNumberBytesEncoder {
		private SplitNumberBytesEncoder7(int nbValues) { super(7, nbValues); }		
		@Override
		public void encode(long v, int vi) {
			bytes[vi] = (byte)(v & 0xFF);
			bytes[nbValues + vi] = (byte)((v & 0xFF00) >> 8);
			bytes[2 * nbValues + vi] = (byte)((v & 0xFF0000) >> 16);
			bytes[3 * nbValues + vi] = (byte)((v & 0xFF000000L) >> 24);
			bytes[4 * nbValues + vi] = (byte)((v & 0xFF00000000L) >> 32);
			bytes[5 * nbValues + vi] = (byte)((v & 0xFF0000000000L) >> 40);
			bytes[6 * nbValues + vi] = (byte)((v & 0xFF000000000000L) >> 48);
		}
	}

	private static class SplitNumberBytesEncoder8 extends SplitNumberBytesEncoder {
		private SplitNumberBytesEncoder8(int nbValues) { super(8, nbValues); }		
		@Override
		public void encode(long v, int vi) {
			bytes[vi] = (byte)(v & 0xFF);
			bytes[nbValues + vi] = (byte)((v & 0xFF00) >> 8);
			bytes[2 * nbValues + vi] = (byte)((v & 0xFF0000) >> 16);
			bytes[3 * nbValues + vi] = (byte)((v & 0xFF000000L) >> 24);
			bytes[4 * nbValues + vi] = (byte)((v & 0xFF00000000L) >> 32);
			bytes[5 * nbValues + vi] = (byte)((v & 0xFF0000000000L) >> 40);
			bytes[6 * nbValues + vi] = (byte)((v & 0xFF000000000000L) >> 48);
			bytes[7 * nbValues + vi] = (byte)(v >> 56);
		}
	}
	
	public abstract static class SplitNumberBytesDecoder {
		protected byte[] bytes;
		protected int nbValues;
		
		protected SplitNumberBytesDecoder(byte[] bytes, int nbValues) {
			this.bytes = bytes;
			this.nbValues = nbValues;
		}
		
		public abstract long decode(int vi);
	}
	
	public static SplitNumberBytesDecoder getSplitNumberBytesDecoder(InputStream in, int nbValues, int nbBytes) throws IOException {
		byte[] bytes = new byte[nbValues * nbBytes];
		IOUtils.readFully(in, bytes);
		switch (nbBytes) {
		case 1: return new SplitNumberBytesDecoder1(bytes, nbValues);
		case 2: return new SplitNumberBytesDecoder2(bytes, nbValues);
		case 3: return new SplitNumberBytesDecoder3(bytes, nbValues);
		case 4: return new SplitNumberBytesDecoder4(bytes, nbValues);
		case 5: return new SplitNumberBytesDecoder5(bytes, nbValues);
		case 6: return new SplitNumberBytesDecoder6(bytes, nbValues);
		case 7: return new SplitNumberBytesDecoder7(bytes, nbValues);
		default: return new SplitNumberBytesDecoder8(bytes, nbValues);
		}
	}
	
	private static class SplitNumberBytesDecoder1 extends SplitNumberBytesDecoder {
		private SplitNumberBytesDecoder1(byte[] bytes, int nbValues) { super(bytes, nbValues); }
		
		@Override
		public long decode(int vi) {
			return bytes[vi] & 0xFF;
		}
	}
	
	private static class SplitNumberBytesDecoder2 extends SplitNumberBytesDecoder {
		private SplitNumberBytesDecoder2(byte[] bytes, int nbValues) { super(bytes, nbValues); }
		
		@Override
		public long decode(int vi) {
			return (bytes[vi] & 0xFF) |
			 ((bytes[nbValues + vi] & 0xFF) << 8);
		}
	}
	
	private static class SplitNumberBytesDecoder3 extends SplitNumberBytesDecoder {
		private SplitNumberBytesDecoder3(byte[] bytes, int nbValues) { super(bytes, nbValues); }
		
		@Override
		public long decode(int vi) {
			return (bytes[vi] & 0xFF) |
			 ((bytes[nbValues + vi] & 0xFF) << 8) |
			 ((bytes[2 * nbValues + vi] & 0xFF) << 16);
		}
	}

	private static class SplitNumberBytesDecoder4 extends SplitNumberBytesDecoder {
		private SplitNumberBytesDecoder4(byte[] bytes, int nbValues) { super(bytes, nbValues); }
		
		@Override
		public long decode(int vi) {
			return (bytes[vi] & 0xFF) |
			 ((bytes[nbValues + vi] & 0xFF) << 8) |
			 ((bytes[2 * nbValues + vi] & 0xFF) << 16) |
			 ((long) (bytes[3 * nbValues + vi] & 0xFF) << 24);
		}
	}
	
	private static class SplitNumberBytesDecoder5 extends SplitNumberBytesDecoder {
		private SplitNumberBytesDecoder5(byte[] bytes, int nbValues) { super(bytes, nbValues); }
		
		@Override
		public long decode(int vi) {
			return (bytes[vi] & 0xFF) |
			 ((bytes[nbValues + vi] & 0xFF) << 8) |
			 ((bytes[2 * nbValues + vi] & 0xFF) << 16) |
			 ((long) (bytes[3 * nbValues + vi] & 0xFF) << 24) |
			 ((long) (bytes[4 * nbValues + vi] & 0xFF) << 32);
		}
	}
	
	private static class SplitNumberBytesDecoder6 extends SplitNumberBytesDecoder {
		private SplitNumberBytesDecoder6(byte[] bytes, int nbValues) { super(bytes, nbValues); }
		
		@Override
		public long decode(int vi) {
			return (bytes[vi] & 0xFF) |
			 ((bytes[nbValues + vi] & 0xFF) << 8) |
			 ((bytes[2 * nbValues + vi] & 0xFF) << 16) |
			 ((long) (bytes[3 * nbValues + vi] & 0xFF) << 24) |
			 ((long) (bytes[4 * nbValues + vi] & 0xFF) << 32) |
			 ((long) (bytes[5 * nbValues + vi] & 0xFF) << 40);
		}
	}
	
	private static class SplitNumberBytesDecoder7 extends SplitNumberBytesDecoder {
		private SplitNumberBytesDecoder7(byte[] bytes, int nbValues) { super(bytes, nbValues); }
		
		@Override
		public long decode(int vi) {
			return (bytes[vi] & 0xFF) |
			 ((bytes[nbValues + vi] & 0xFF) << 8) |
			 ((bytes[2 * nbValues + vi] & 0xFF) << 16) |
			 ((long) (bytes[3 * nbValues + vi] & 0xFF) << 24) |
			 ((long) (bytes[4 * nbValues + vi] & 0xFF) << 32) |
			 ((long) (bytes[5 * nbValues + vi] & 0xFF) << 40) |
			 ((long) (bytes[6 * nbValues + vi] & 0xFF) << 48);
		}
	}
	
	private static class SplitNumberBytesDecoder8 extends SplitNumberBytesDecoder {
		private SplitNumberBytesDecoder8(byte[] bytes, int nbValues) { super(bytes, nbValues); }
		
		@Override
		public long decode(int vi) {
			return (bytes[vi] & 0xFF) |
			 ((bytes[nbValues + vi] & 0xFF) << 8) |
			 ((bytes[2 * nbValues + vi] & 0xFF) << 16) |
			 ((long) (bytes[3 * nbValues + vi] & 0xFF) << 24) |
			 ((long) (bytes[4 * nbValues + vi] & 0xFF) << 32) |
			 ((long) (bytes[5 * nbValues + vi] & 0xFF) << 40) |
			 ((long) (bytes[6 * nbValues + vi] & 0xFF) << 48) |
			 ((long) (bytes[7 * nbValues + vi] & 0xFF) << 56);
		}
	}
	
	public static interface TransformLong {
		long transform(long v);
	}
	public static TransformLong getTransformLong(TransformLong... transforms) {
		TransformLong[] array;
		int nb = 0;
		for (int i = 0; i < transforms.length; ++i) if (transforms[i] != null) nb++;
		if (nb == 0) return v -> v;
		array = new TransformLong[nb];
		int j = 0;
		for (int i = 0; i < transforms.length; ++i) if (transforms[i] != null) array[j++] = transforms[i];
		return v -> {
			for (int i = 0; i < array.length; ++i)
				v = array[i].transform(v);
			return v;
		};
	}
	
}
