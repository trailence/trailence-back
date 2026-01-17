package org.trailence.trail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.trailence.global.AccessibleByteArrayOutputStream;
import org.trailence.global.IOUtils;
import org.trailence.global.TrailenceUtils;
import org.trailence.trail.dto.Track;
import org.trailence.trail.dto.Track.Segment;
import org.trailence.trail.dto.Track.WayPoint;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public class TrackStorage {

	public static class V1 {
		@SuppressWarnings("java:S1104") // only used for serialization
		@NoArgsConstructor
		@AllArgsConstructor
		public static class StoredData {
			public Segment[] s;
			public WayPoint[] wp;
		}
		
		public static byte[] compress(StoredData value) throws IOException {
			try (AccessibleByteArrayOutputStream bos = new AccessibleByteArrayOutputStream(8192);
				GZIPOutputStream gos = new GZIPOutputStream(bos)) {
				TrailenceUtils.mapper.writeValue(gos, value);
				byte[] result = bos.getData();
				int len = bos.getLength();
				if (result.length == len) return result;
				byte[] compressed = new byte[len];
				System.arraycopy(result, 0, compressed, 0, len);
				return compressed;
			}
		}
		
		public static StoredData uncompress(byte[] compressed) throws IOException {
			try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
				GZIPInputStream gis = new GZIPInputStream(bis)) {
				return TrailenceUtils.mapper.readValue(gis, new TypeReference<StoredData>() {});
			}
		}
	}
	
	public static class V1V2Bridge {
		
		private static final int WP_BYTES = 4 + 4 + 3 + 8;
		
		private static class Info {
			boolean hasElevation = false;
			boolean hasTime = false;
			boolean hasPa = false;
			boolean hasEa = false;
			int nbSegments = 0;
			int nbWaypoints = 0;
			
			public void encode(OutputStream out) throws IOException {
				int i = (hasElevation ? 0x01 : 0) |
						(hasTime ? 0x02 : 0) |
						(hasPa ? 0x04 : 0) |
						(hasEa ? 0x08 : 0);
				if (nbSegments == 2)
					i |= 0x10;
				else if (nbSegments == 3)
					i |= 0x20;
				else if (nbSegments > 3)
					i |= 0x30;
				if (nbWaypoints == 1)
					i |= 0x40;
				else if (nbWaypoints == 2)
					i |= 0x80;
				else if (nbWaypoints > 2)
					i |= 0xC0;
				out.write(i);
				if (nbSegments > 3) encodeInteger2(nbSegments - 3, out);
				if (nbWaypoints > 2) encodeInteger2(nbWaypoints - 2, out);
			}
			
			public static Info decode(InputStream in) throws IOException {
				Info info = new Info();
				int i = in.read();
				if ((i & 0x01) != 0) info.hasElevation = true;
				if ((i & 0x02) != 0) info.hasTime = true;
				if ((i & 0x04) != 0) info.hasPa = true;
				if ((i & 0x08) != 0) info.hasEa = true;
				switch ((i & 0x30) >> 4) {
				case 0: info.nbSegments = 1; break;
				case 1: info.nbSegments = 2; break;
				case 2: info.nbSegments = 3; break;
				default: info.nbSegments = decodeInteger2(in) + 3;
				}
				switch ((i & 0xC0) >> 6) {
				case 0: info.nbWaypoints = 0; break;
				case 1: info.nbWaypoints = 1; break;
				case 2: info.nbWaypoints = 2; break;
				default: info.nbWaypoints = decodeInteger2(in) + 2;
				}
				return info;
			}
		}
		
		public static byte[] v1DtoToV2(V1.StoredData value) throws IOException {
			try (AccessibleByteArrayOutputStream bos = new AccessibleByteArrayOutputStream(8192)) {
				Deflater def = new Deflater(Deflater.BEST_COMPRESSION, true);
				try (DeflaterOutputStream gos = new DeflaterOutputStream(bos, def, 2048);
					BufferedOutputStream out = new BufferedOutputStream(gos, 8192)) {
					Info info = new Info();
					info.nbSegments = value.s.length;
					info.nbWaypoints = value.wp.length;
					for (var segment : value.s) {
						for (var point : segment.getP()) {
							if (!info.hasElevation && point.getE() != null) info.hasElevation = true;
							if (!info.hasTime && point.getT() != null) info.hasTime = true;
							if (!info.hasPa && point.getPa() != null) info.hasPa = true;
							if (!info.hasEa && point.getEa() != null) info.hasEa = true;
							if (info.hasElevation && info.hasTime && info.hasPa && info.hasEa) break;
						}
						if (info.hasElevation && info.hasTime && info.hasPa && info.hasEa) break;
					}
					info.encode(out);
					for (var segment : value.s) {
						encodeInteger2to4(segment.getP().length, out);
					}
					for (var segment : value.s) {
						var points = segment.getP();
						int nb = points.length;
						if (nb == 0) continue;
						// latitude goes from -90 to 90, with factor 10000000 => -900000000 to 900000000 => 32 bits integer
						// longitude goes from -180 to 180, with factor 10000000 => -1800000000 to 1800000000 => 32 bits integer
						// elevation goes from -10000 to 10000, with factor 10 => -100000 to 100000 => 24 bits integer
						// time is 64 bits integer
						// posAccuracy, with factor 100: 10km would be 1000000 => 24 bits integer
						// eleAccuracy, with factor 100: same 24 bits integer
						// forget about heading and speed

						Long v = points[0].getL();
						long l = v == null ? 0 : v.longValue();
						if (l < -900000000 || l > 900000000) throw new IOException("Invalid latitude: " + l);
						l = toUnsigned(l);
						encodeNumber(out, l, 4);

						v = points[0].getN();
						l = v == null ? 0 : v.longValue();
						if (l < -1800000000 || l > 1800000000) throw new IOException("Invalid longitude: " + l);
						l = toUnsigned(l);
						encodeNumber(out, l, 4);

						if (info.hasElevation) {
							v = points[0].getE();
							l = toUnsigned(encode24bitsNullable(v));
							encodeNumber(out, l, 3);
						}
						if (info.hasTime) {
							v = points[0].getT();
							l = encode64bitsNullable(v);
							encodeNumber(out, l, 8);
						}

						if (info.hasPa) {
							v = points[0].getPa();
							l = toUnsigned(encode24bitsNullable(v));
							encodeNumber(out, l, 3);
						}
							
						if (info.hasEa) {
							v = points[0].getEa();
							l = toUnsigned(encode24bitsNullable(v));
							encodeNumber(out, l, 3);
						}
						
						if (nb > 1) {
							long[] values = new long[nb - 1];
							for (int i = 0; i < nb - 1; ++i) {
								v = points[i + 1].getL();
								values[i] = v == null ? 0 : v.longValue();
							}
							encodeCoord(out, values);
							
							for (int i = 0; i < nb - 1; ++i) {
								v = points[i + 1].getN();
								values[i] = v == null ? 0 : v.longValue();
							}
							encodeCoord(out, values);
							
							Long[] nullableValues = new Long[nb - 1];

							if (info.hasElevation) {
								for (int i = 0; i < nb - 1; ++i) nullableValues[i] = points[i + 1].getE();
								encodeElevation(out, nullableValues);
							}
							
							if (info.hasTime) {
								for (int i = 0; i < nb - 1; ++i) nullableValues[i] = points[i + 1].getT();
								encodeTime(out, nullableValues);
							}
							
							if (info.hasPa) {
								for (int i = 0; i < nb - 1; ++i) nullableValues[i] = points[i + 1].getPa();
								encodeAccuracy(out, nullableValues);
							}

							if (info.hasEa) {
								for (int i = 0; i < nb - 1; ++i) nullableValues[i] = points[i + 1].getEa();
								encodeAccuracy(out, nullableValues);
							}
						}
					}
					int nb = value.wp.length;
					if (nb > 0) {
						byte[] bytes = new byte[nb * WP_BYTES];
						for (int i = 0; i < nb; ++i) {
							// expect elevation first byte to be 0
							// expect time to be on 6 bytes
							Long v = value.wp[i].getL();
							if (v != null && (v.longValue() < -900000000 || v.longValue() > 900000000)) throw new IOException("Invalid latitude: " + v);
							long l = toUnsigned(encode32bitsNullable(v));
							encodePoint32Bits(bytes, nb, i, l, new int[] { 4, 6, 8, 10 });
							
							v = value.wp[i].getN();
							if (v != null && (v.longValue() < -1800000000 || v.longValue() > 1800000000)) throw new IOException("Invalid longitude: " + v);
							l = toUnsigned(encode32bitsNullable(v));
							encodePoint32Bits(bytes, nb, i, l, new int[] { 5, 7, 9, 11 });
							
							v = value.wp[i].getE();
							l = toUnsigned(encode24bitsNullable(v));
							encodePoint24Bits(bytes, nb, i, l, new int[] { 0, 3, 12 });
							
							v = value.wp[i].getT();
							l = encode64bitsNullable(v);
							encodePoint64Bits(bytes, nb, i, l, new int[] { 1, 2, 13, 14, 15, 16, 17, 18 });
						}
						out.write(bytes);

						for (var wp : value.wp) {
							encodeString(out, wp.getNa());
							encodeString(out, wp.getDe());
							encodeStringMap(out, wp.getNt());
							encodeStringMap(out, wp.getDt());
						}
					}
				}
				def.end();
				byte[] result = bos.getData();
				int len = bos.getLength();
				if (result.length == len) return result;
				byte[] compressed = new byte[len];
				System.arraycopy(result, 0, compressed, 0, len);
				return compressed;
			}
		}
		
		public static V1.StoredData v2ToV1Dto(byte[] v2) throws IOException {
			try (ByteArrayInputStream bis = new ByteArrayInputStream(v2)) {
				Inflater inf = new Inflater(true);
				try (InflaterInputStream gis = new InflaterInputStream(bis, inf, 4096);
					BufferedInputStream in = new BufferedInputStream(gis, 8192)) {
					Info info = Info.decode(in);
					Track.Point[][] segments = new Track.Point[info.nbSegments][];
					for (int i = 0; i < info.nbSegments; ++i) {
						segments[i] = new Track.Point[decodeInteger2to4(in)];
					}
					for (int s = 0; s < info.nbSegments; ++s) {
						int nb = segments[s].length;
						if (nb == 0) continue;
						// first point
						long lat = toSigned(decodeNumber(in, 4));
						long lon = toSigned(decodeNumber(in, 4));
						Long ele = info.hasElevation ? decode24bitsNullable(toSigned(decodeNumber(in, 3))) : null;
						Long tim = info.hasTime ? decode64bitsNullable(decodeNumber(in, 8)) : null;
						Long pa = info.hasPa ? decode24bitsNullable(toSigned(decodeNumber(in, 3))) : null;
						Long ea = info.hasEa ? decode24bitsNullable(toSigned(decodeNumber(in, 3))) : null;
						segments[s][0] = new Track.Point(lat == 0 ? null : lat, lon == 0 ? null : lon, ele, tim, pa, ea, null, null);
						
						if (nb > 1) {
							long[] latitudes = new long[nb - 1];
							decodeCoord(in, latitudes);
							long[] longitudes = new long[nb - 1];
							decodeCoord(in, longitudes);

							Long[] elevations = new Long[nb - 1];
							if (info.hasElevation) {
								decodeElevation(in, elevations);
							}
							Long[] times = new Long[nb - 1];
							if (info.hasTime) {
								decodeTime(in, times);
							}
							Long[] pas = new Long[nb - 1];
							if (info.hasPa) {
								decodeAccuracy(in, pas);
							}
							Long[] eas = new Long[nb - 1];
							if (info.hasEa) {
								decodeAccuracy(in, eas);
							}
							for (int i = 0; i < nb - 1; ++i)
								segments[s][i + 1] = new Track.Point(
									latitudes[i] == 0 ? null : latitudes[i],
									longitudes[i] == 0 ? null : longitudes[i],
									elevations[i],
									times[i],
									pas[i],
									eas[i],
									null, null
								);
						}
					}
					V1.StoredData v1 = new V1.StoredData();
					v1.s = new Track.Segment[info.nbSegments];
					for (int i = 0; i < info.nbSegments; ++i) {
						v1.s[i] = new Track.Segment(segments[i]);
					}
					
					int nb = info.nbWaypoints;
					v1.wp = new Track.WayPoint[nb];
					if (nb > 0) {
						byte[] bytes = new byte[nb * WP_BYTES];
						IOUtils.readFully(in, bytes);
						for (int i = 0; i < nb; ++i) {
							Long lat = decode32bitsNullable(toSigned(decodePoint32Bits(bytes, nb, i, new int[] { 4, 6, 8, 10 })));
							Long lon = decode32bitsNullable(toSigned(decodePoint32Bits(bytes, nb, i, new int[] { 5, 7, 9, 11 })));
							Long ele = decode24bitsNullable(toSigned(decodePoint24Bits(bytes, nb, i, new int[] { 0, 3, 12 })));
							Long tim = decode64bitsNullable(decodePoint64Bits(bytes, nb, i, new int[] { 1, 2, 13, 14, 15, 16, 17, 18 }));
							v1.wp[i] = new Track.WayPoint(lat, lon, ele, tim, null, null, null, null);
						}
						for (int i = 0; i < nb; ++i) {
							v1.wp[i].setNa(decodeString(in));
							v1.wp[i].setDe(decodeString(in));
							v1.wp[i].setNt(decodeStringMap(in));
							v1.wp[i].setDt(decodeStringMap(in));
						}
					}

					return v1;
				} finally {
					inf.end();
				}
			}
		}

		
		/**
		 * Encode an integer between 0 and 0x7FFF:<ul>
		 * <li>First byte contains a flag 0x80: if set the value is encoded on 2 bytes, else on 1 byte</li>
		 * </ul>
		 */
		private static void encodeInteger2(int value, OutputStream out) throws IOException {
			if (value < 0 || value > 0x7FFF) throw new IOException("Invalid integer value: " + value);
			if (value <= 0x7F) {
				out.write(value);
				return;
			}
			out.write(((value & 0x7F00) >> 8) | 0x80);
			out.write(value & 0xFF);
		}
		
		private static int decodeInteger2(InputStream in) throws IOException {
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
		private static void encodeInteger2to4(int value, OutputStream out) throws IOException {
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
		
		private static int decodeInteger2to4(InputStream in) throws IOException {
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
		
		private static void encodePoint24Bits(byte[] bytes, int nbPoints, int pointIndex, long v, int[] offset) {
			bytes[offset[2] * nbPoints + pointIndex] = (byte)(v & 0xFF);
			bytes[offset[1] * nbPoints + pointIndex] = (byte)((v & 0xFF00) >> 8);
			bytes[offset[0] * nbPoints + pointIndex] = (byte)((v & 0xFF0000) >> 16);
		}

		private static long decodePoint24Bits(byte[] bytes, int nbPoints, int pointIndex, int[] offset) {
			return (bytes[offset[2] * nbPoints + pointIndex] & 0xFF) |
				((bytes[offset[1] * nbPoints + pointIndex] & 0xFF) << 8) |
				((bytes[offset[0] * nbPoints + pointIndex] & 0xFF) << 16);
		}
		
		private static void encodePoint32Bits(byte[] bytes, int nbPoints, int pointIndex, long v, int[] offset) {
			bytes[offset[3] * nbPoints + pointIndex] = (byte)(v & 0xFF);
			bytes[offset[2] * nbPoints + pointIndex] = (byte)((v & 0xFF00) >> 8);
			bytes[offset[1] * nbPoints + pointIndex] = (byte)((v & 0xFF0000) >> 16);
			bytes[offset[0] * nbPoints + pointIndex] = (byte)((v & 0xFF000000) >> 24);
		}
		
		private static long decodePoint32Bits(byte[] bytes, int nbPoints, int pointIndex, int[] offset) {
			return (bytes[offset[3] * nbPoints + pointIndex] & 0xFF) |
				((bytes[offset[2] * nbPoints + pointIndex] & 0xFF) << 8) |
				((bytes[offset[1] * nbPoints + pointIndex] & 0xFF) << 16) |
				((long)(bytes[offset[0] * nbPoints + pointIndex] & 0xFF) << 24);
		}
		
		private static void encodePoint64Bits(byte[] bytes, int nbPoints, int pointIndex, long v, int[] offset) {
			byte[] b = new byte[8];
			ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).putLong(v);
			bytes[offset[0] * nbPoints + pointIndex] = b[0];
			bytes[offset[1] * nbPoints + pointIndex] = b[1];
			bytes[offset[2] * nbPoints + pointIndex] = b[2];
			bytes[offset[3] * nbPoints + pointIndex] = b[3];
			bytes[offset[4] * nbPoints + pointIndex] = b[4];
			bytes[offset[5] * nbPoints + pointIndex] = b[5];
			bytes[offset[6] * nbPoints + pointIndex] = b[6];
			bytes[offset[7] * nbPoints + pointIndex] = b[7];
		}
		
		private static long decodePoint64Bits(byte[] bytes, int nbPoints, int pointIndex, int[] offset) {
			byte[] b = new byte[8];
			b[0] = bytes[offset[0] * nbPoints + pointIndex];
			b[1] = bytes[offset[1] * nbPoints + pointIndex];
			b[2] = bytes[offset[2] * nbPoints + pointIndex];
			b[3] = bytes[offset[3] * nbPoints + pointIndex];
			b[4] = bytes[offset[4] * nbPoints + pointIndex];
			b[5] = bytes[offset[5] * nbPoints + pointIndex];
			b[6] = bytes[offset[6] * nbPoints + pointIndex];
			b[7] = bytes[offset[7] * nbPoints + pointIndex];
			return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getLong();
		}
		
		private static void encodeNumber(OutputStream out, long v, int bytes) throws IOException {
			if (bytes == 8) {
				byte[] b = new byte[8];
				ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putLong(v);
				out.write(b);
				return;
			}
			out.write((int)(v & 0xFF));
			if (bytes > 1) out.write((int)((v & 0xFF00) >> 8));
			if (bytes > 2) out.write((int)((v & 0xFF0000) >> 16));
			if (bytes > 3) out.write((int)((v & 0xFF000000) >> 24));
			if (bytes > 4) out.write((int)((v & 0xFF00000000L) >> 32));
			if (bytes > 5) out.write((int)((v & 0xFF0000000000L) >> 40));
			if (bytes > 6) out.write((int)((v >> 48) & 0xFF));
		}
		
		private static long decodeNumber(InputStream in, int bytes) throws IOException {
			if (bytes == 8) {
				byte[] b = new byte[8];
				IOUtils.readFully(in, b);
				return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong();
			}
			long v = in.read();
			if (bytes > 1) v |= in.read() << 8;
			if (bytes > 2) v |= in.read() << 16;
			if (bytes > 3) v |= ((long)in.read()) << 24;
			if (bytes > 4) v |= ((long)in.read()) << 32;
			if (bytes > 5) v |= ((long)in.read()) << 40;
			if (bytes > 6) v |= ((long)in.read()) << 48;
			return v;
		}
		
		private static void encodeSplitNumber(long v, int vi, byte[] bytes, int nbValues, int nbBytes) {
			encodeSplitByte((byte)(v & 0xFF), 0, vi, bytes, nbValues);
			if (nbBytes == 1) return;
			encodeSplitByte((byte)((v & 0xFF00) >> 8), 1, vi, bytes, nbValues);
			if (nbBytes == 2) return;
			encodeSplitByte((byte)((v & 0xFF0000) >> 16), 2, vi, bytes, nbValues);
			if (nbBytes == 3) return;
			encodeSplitByte((byte)((v & 0xFF000000L) >> 24), 3, vi, bytes, nbValues);
			if (nbBytes == 4) return;
			encodeSplitByte((byte)((v & 0xFF00000000L) >> 32), 4, vi, bytes, nbValues);
			if (nbBytes == 5) return;
			encodeSplitByte((byte)((v & 0xFF0000000000L) >> 40), 5, vi, bytes, nbValues);
			if (nbBytes == 6) return;
			encodeSplitByte((byte)((v & 0xFF000000000000L) >> 48), 6, vi, bytes, nbValues);
			if (nbBytes == 7) return;
			encodeSplitByte((byte)(v >> 56), 7, vi, bytes, nbValues);
		}
		
		private static void encodeSplitByte(byte b, int bi, int vi, byte[] bytes, int nbValues) {
			bytes[bi * nbValues + vi] = b;
		}
		
		private static long decodeSplitNumber(byte[] bytes, int vi, int nbValues, int nbBytes) {
			long v = decodeSplitByte(bytes, 0, vi, nbValues);
			if (nbBytes == 1) return v;
			v |= decodeSplitByte(bytes, 1, vi, nbValues) << 8;
			if (nbBytes == 2) return v;
			v |= decodeSplitByte(bytes, 2, vi, nbValues) << 16;
			if (nbBytes == 3) return v;
			v |= ((long)decodeSplitByte(bytes, 3, vi, nbValues)) << 24;
			if (nbBytes == 4) return v;
			v |= ((long)decodeSplitByte(bytes, 4, vi, nbValues)) << 32;
			if (nbBytes == 5) return v;
			v |= ((long)decodeSplitByte(bytes, 5, vi, nbValues)) << 40;
			if (nbBytes == 6) return v;
			v |= ((long)decodeSplitByte(bytes, 6, vi, nbValues)) << 48;
			if (nbBytes == 7) return v;
			v |= ((long)decodeSplitByte(bytes, 7, vi, nbValues)) << 56;
			return v;
		}
		
		private static int decodeSplitByte(byte[] bytes, int bi, int vi, int nbValues) {
			return bytes[bi * nbValues + vi] & 0xFF;
		}
		
		private static void encodeSplitNumbers(OutputStream out, Long[] values, int encodingLength, boolean hasNegative, long negativeBit) throws IOException {
			int nbValues = values.length;
			int nbBytes = encodingLength + 1;
			byte[] bytes = new byte[nbValues * nbBytes];
			for (int vi = 0; vi < nbValues; ++vi) {
				if (nbBytes == 8 || !hasNegative)
					encodeSplitNumber(values[vi], vi, bytes, nbValues, nbBytes);
				else {
					long v = values[vi];
					boolean sign = v < 0;
					long abs = Math.abs(v);
					if (sign) abs |= negativeBit;
					encodeSplitNumber(abs, vi, bytes, nbValues, nbBytes);
				}
			}
			out.write(bytes);
		}

		private static void encodeSplitNonNullNumbers(OutputStream out, long[] values, int encodingLength, boolean hasNegative, long negativeBit) throws IOException {
			int nbValues = values.length;
			int nbBytes = encodingLength + 1;
			byte[] bytes = new byte[nbValues * nbBytes];
			for (int vi = 0; vi < nbValues; ++vi) {
				if (nbBytes == 8 || !hasNegative)
					encodeSplitNumber(values[vi], vi, bytes, nbValues, nbBytes);
				else {
					long v = values[vi];
					boolean sign = v < 0;
					long abs = Math.abs(v);
					if (sign) abs |= negativeBit;
					encodeSplitNumber(abs, vi, bytes, nbValues, nbBytes);
				}
			}
			out.write(bytes);
		}

		private static void encodeSplitNonNullNumbersNegative1(OutputStream out, long[] values, int encodingLength, boolean hasNegative) throws IOException {
			int nbValues = values.length;
			int nbBytes = encodingLength + 1;
			byte[] bytes = new byte[nbValues * nbBytes];
			for (int vi = 0; vi < nbValues; ++vi) {
				if (nbBytes == 8 || !hasNegative)
					encodeSplitNumber(values[vi], vi, bytes, nbValues, nbBytes);
				else {
					long v = values[vi];
					boolean sign = v < 0;
					long abs = Math.abs(v) << 1;
					if (sign) abs |= 1;
					encodeSplitNumber(abs, vi, bytes, nbValues, nbBytes);
				}
			}
			out.write(bytes);
		}

		private static void decodeSplitNumbers(InputStream in, Long[] values, int encodingLength, boolean hasNegative, boolean[] isNull, int addLastDigit, long negativeBit) throws IOException {
			int totalValues = values.length;
			int nbValues = totalValues;
			for (int i = 0; i < totalValues; ++i) if (isNull[i]) nbValues--;
			int nbBytes = encodingLength + 1;
			byte[] bytes = new byte[nbValues * nbBytes];
			IOUtils.readFully(in, bytes);
			int vi = 0;
			for (int i = 0; i < totalValues; ++i) {
				if (isNull[i]) continue;
				long l = decodeSplitNumber(bytes, vi++, nbValues, nbBytes);
				if (hasNegative && encodingLength < 7) {
					boolean sign = (l & negativeBit) != 0;
					if (sign) l = -(l - negativeBit);
				}
				for (int j = 0; j < addLastDigit; ++j) l = l * 10;
				values[i] = l;
			}
		}

		private static void decodeSplitNonNullNumbers(InputStream in, long[] values, int encodingLength, boolean hasNegative, int addLastDigit, long negativeBit) throws IOException {
			int nbValues = values.length;
			int nbBytes = encodingLength + 1;
			byte[] bytes = new byte[nbValues * nbBytes];
			IOUtils.readFully(in, bytes);
			for (int vi = 0; vi < nbValues; ++vi) {
				long l = decodeSplitNumber(bytes, vi, nbValues, nbBytes);
				if (hasNegative && encodingLength < 7) {
					boolean sign = (l & negativeBit) != 0;
					if (sign) l = -(l - negativeBit);
				}
				for (int j = 0; j < addLastDigit; ++j) l = l * 10;
				values[vi] = l;
			}
		}

		private static void decodeSplitNonNullNumbersNegative1(InputStream in, long[] values, int encodingLength, boolean hasNegative, int addLastDigit) throws IOException {
			int nbValues = values.length;
			int nbBytes = encodingLength + 1;
			byte[] bytes = new byte[nbValues * nbBytes];
			IOUtils.readFully(in, bytes);
			for (int vi = 0; vi < nbValues; ++vi) {
				long l = decodeSplitNumber(bytes, vi, nbValues, nbBytes);
				if (hasNegative && encodingLength < 7) {
					boolean sign = (l & 1) != 0;
					l = l >> 1;
					if (sign) l = -l;
				}
				for (int j = 0; j < addLastDigit; ++j) l = l * 10;
				values[vi] = l;
			}
		}
		
		private static void encodeSplitBitsNonNullNumbers(OutputStream out, long[] values, int encodingLength, boolean hasNegative, long negativeBit) throws IOException {
			BitEncoder bits = new BitEncoder(out);
			int nbValues = values.length;
			int nbBytes = encodingLength + 1;
			for (int byteIndex = 0; byteIndex < nbBytes; byteIndex++) {
				for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
					for (int vi = 0; vi < nbValues; ++vi) {
						long v = values[vi];
						if (nbBytes < 8 || hasNegative) {
							boolean sign = v < 0;
							v = Math.abs(v);
							if (sign) v |= negativeBit;
						}
						boolean bit = ((v >> (byteIndex * 8 + bitIndex)) & 1) != 0;
						bits.encode(bit);
					}
				}
			}
			bits.close();
		}
		
		private static void decodeSplitBitsNonNullNumbers(InputStream in, long[] values, int encodingLength, boolean hasNegative, int addLastDigit, long negativeBit) throws IOException {
			int nbValues = values.length;
			int nbBytes = encodingLength + 1;
			BitDecoder bits = new BitDecoder(in);
			for (int byteIndex = 0; byteIndex < nbBytes; byteIndex++) {
				for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
					for (int vi = 0; vi < nbValues; ++vi) {
						boolean bit = bits.decode();
						if (bit) values[vi] |= 1L << (byteIndex * 8 + bitIndex);
					}
				}
			}
			if (nbBytes < 8 || hasNegative) {
				for (int vi = 0; vi < nbValues; ++vi) {
					if ((values[vi] & negativeBit) != 0)
						values[vi] = -(values[vi] - negativeBit);
				}
			}
			if (addLastDigit > 0) {
				for (int vi = 0; vi < nbValues; ++vi) {
					for (int j = 0; j < addLastDigit; ++j) values[vi] = values[vi] * 10;
				}
			}
		}
		
		private static void encodeSplitBitsNumbers(BitEncoder bits, Long[] values, int encodingLength, boolean hasNegative, long negativeBit) throws IOException {
			int nbValues = values.length;
			int nbBytes = encodingLength + 1;
			for (int byteIndex = 0; byteIndex < nbBytes; byteIndex++) {
				for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
					for (int vi = 0; vi < nbValues; ++vi) {
						long v = values[vi];
						if (nbBytes < 8 && hasNegative) {
							boolean sign = v < 0;
							v = Math.abs(v);
							if (sign) v |= negativeBit;
						}
						boolean bit = ((v >> (byteIndex * 8 + bitIndex)) & 1) != 0;
						bits.encode(bit);
					}
				}
			}
		}

		private static void decodeSplitBitsNumbers(BitDecoder bits, Long[] values, int encodingLength, boolean hasNegative, boolean[] isNull, int addLastDigit, long negativeBit) throws IOException {
			int totalValues = values.length;
			int nbBytes = encodingLength + 1;
			for (int byteIndex = 0; byteIndex < nbBytes; byteIndex++) {
				for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
					for (int vi = 0; vi < totalValues; ++vi) {
						if (isNull[vi]) continue;
						if (values[vi] == null) values[vi] = 0L;
						boolean bit = bits.decode();
						if (bit) values[vi] |= 1L << (byteIndex * 8 + bitIndex);
					}
				}
			}
			if (nbBytes < 8 && hasNegative) {
				for (int vi = 0; vi < totalValues; ++vi) {
					if (isNull[vi]) continue;
					if ((values[vi] & negativeBit) != 0)
						values[vi] = -(values[vi] - negativeBit);
				}
			}
			if (addLastDigit > 0) {
				for (int vi = 0; vi < totalValues; ++vi) {
					if (isNull[vi]) continue;
					for (int j = 0; j < addLastDigit; ++j) values[vi] = values[vi] * 10;
				}
			}
		}
		
		private static void predictValues(Long[] values) {
			long previous = values[0];
			for (int i = 1; i < values.length; ++i) {
				long v = values[i];
				values[i] -= previous;
				previous = v;
			}
		}
		private static void unpredictValues(Long[] values) {
			int i = 0;
			while (values[i] == null) ++i;
			long previous = values[i++];
			for (; i < values.length; ++i) {
				if (values[i] == null) continue;
				values[i] += previous;
				previous = values[i];
			}
		}
		
		
		private static void encodeCoord(OutputStream out, long[] values) throws IOException {
			// 1800000000 = 0x6B49D200 => +1 bit for sign = 32 bits

			BitEncoder bits = new BitEncoder(out);
			DigitEncoder digits = new DigitEncoder();
			int nbLastDigit = 0;
			for (var v : values) {
				if ((v % 10) != 0) nbLastDigit++;
				bits.encode(v < 0);
			}
			if (nbLastDigit == 0) {
				bits.encode(false);
				for (int i = 0; i < values.length; ++i)
					values[i] = Math.abs(values[i]) / 10;
			} else if (nbLastDigit < values.length / 2) {
				bits.encode(true);
				bits.encode(true);
				for (int i = 0; i < values.length; ++i) {
					long v = Math.abs(values[i]);
					int mod = (int)(v % 10);
					values[i] = v / 10;
					bits.encode(mod != 0);
					if (mod != 0) {
						digits.encode(mod);
					}
				}
			} else {
				bits.encode(true);
				bits.encode(false);
				for (int i = 0; i < values.length; ++i)
					values[i] = Math.abs(values[i]);
			}
			
			long maxAbs = values[0];
			for (int i = 1; i < values.length; ++i) {
				long v = values[i];
				if (v > maxAbs) maxAbs = v;
			}
			
			int encodingLength;
			if (maxAbs < 0x100) encodingLength = 0;
			else if (maxAbs < 0x10000) encodingLength = 1;
			else if (maxAbs < 0x1000000) encodingLength = 2;
			else encodingLength = 3;
			bits.encodeNumber(encodingLength, 2);
			bits.close();
			encodeSplitBitsNonNullNumbers(out, values, encodingLength, false, 0);
			digits.close(out);
		}
		
		private static void decodeCoord(InputStream in, long[] values) throws IOException {
			BitDecoder bits = new BitDecoder(in);
			boolean[] sign = new boolean[values.length];
			for (int i = 0; i < values.length; ++i) sign[i] = bits.decode();
			boolean hasLastDigit = bits.decode();
			boolean isLastDigitEncoded = false;
			boolean[] valuesLastDigit = new boolean[0];
			if (hasLastDigit) {
				isLastDigitEncoded = bits.decode();
				if (isLastDigitEncoded) {
					valuesLastDigit = new boolean[values.length];
					for (int i = 0; i < values.length; ++i)
						valuesLastDigit[i] = bits.decode();
				}
			}
			int encodingLength = bits.decodeNumber(2);
			decodeSplitBitsNonNullNumbers(in, values, encodingLength, false, hasLastDigit && !isLastDigitEncoded ? 0 : 1, 0);
			if (isLastDigitEncoded) {
				DigitDecoder digits = new DigitDecoder(in);
				for (int i = 0; i < values.length; ++i)
					if (valuesLastDigit[i]) {
						int digit = digits.decode();
						values[i] += digit;
					}
			}
			for (int i = 0; i < values.length; ++i) {
				if (sign[i]) values[i] = -values[i];
			}
		}
		
		private static void encodeElevation(OutputStream out, Long[] values) throws IOException {
			// elevation goes from -10000 to 10000, with factor 10 => -100000 to 100000 => 24 bits integer
			// 100000 = 0x186A0
			// 0x7FFF = 32767 = 3276.7 meters
			
			BitEncoder bits = new BitEncoder(out);
			
			int nbNull = 0;
			for (var v : values) if (v == null) nbNull++;
			if (nbNull == 0) {
				bits.encode(false);
			} else {
				bits.encode(true);
				for (var v : values) bits.encode(v == null);
				Long[] newValues = new Long[values.length - nbNull];
				int pos = 0;
				for (int i = 0; i < values.length; ++i) if (values[i] != null) newValues[pos++] = values[i];
				values = newValues;
			}
			if (values.length == 0) {
				bits.close();
				return;
			}
			long maxAbs = 0;
			for (int i = 0; i < values.length; ++i) {
				long v = values[i];
				if (v < 0) {
					bits.encode(true);
					v = -v;
					values[i] = v;
				} else {
					bits.encode(false);
				}
				if (v > maxAbs) maxAbs = v;
			}
			int encodingLength;
			if (maxAbs < 0x100) encodingLength = 0;
			else if (maxAbs < 0x10000) encodingLength = 1;
			else encodingLength = 2;
			bits.encode((encodingLength & 1) != 0);
			bits.encode((encodingLength & 2) != 0);
			
			bits.close();
			encodeSplitNumbers(out, values, encodingLength, false, 0);
		}
		
		private static void decodeElevation(InputStream in, Long[] values) throws IOException {
			BitDecoder bits = new BitDecoder(in);
			
			boolean hasNull = bits.decode();
			boolean[] isNull = new boolean[values.length];
			boolean allNull = hasNull;
			if (hasNull) {
				for (int i = 0; i < values.length; ++i) {
					isNull[i] = bits.decode();
					allNull = allNull && isNull[i];
				}
			}
			if (allNull) return;
			boolean[] sign = new boolean[values.length];
			for (int i = 0; i < values.length; ++i) {
				if (isNull[i]) sign[i] = false;
				else sign[i] = bits.decode();
			}
			
			int encodingLength = (bits.decode() ? 1 : 0) | (bits.decode() ? 2 : 0);
			decodeSplitNumbers(in, values, encodingLength, false, isNull, 0, 0);
			for (int i = 0; i < values.length; ++i)
				if (sign[i]) values[i] = -values[i];
		}

		private static void encodeTime(OutputStream out, Long[] values) throws IOException {
			BitEncoder bits = new BitEncoder(out);
			
			int nbNull = 0;
			for (var v : values) if (v == null) nbNull++;
			if (nbNull == 0) {
				bits.encode(false);
			} else {
				bits.encode(true);
				for (var v : values) bits.encode(v == null);
				Long[] newValues = new Long[values.length - nbNull];
				int pos = 0;
				for (int i = 0; i < values.length; ++i) if (values[i] != null) newValues[pos++] = values[i];
				values = newValues;
			}
			if (values.length == 0) {
				bits.close();
				return;
			}
			boolean hasNegative = false;
			for (long l : values) if (l < 0) { hasNegative = true; break; }
			bits.encode(hasNegative);
			
			for (int i = 0; i < 3; ++i) {
				boolean hasLastDigit = false;
				for (long l : values) if ((l % 10) != 0) { hasLastDigit = true; break; }
				bits.encode(hasLastDigit);
				if (hasLastDigit) break;
				for (int j = 0; j < values.length; ++j) values[j] = values[j] / 10;
			}
			
			long maxAbs = Math.abs(values[0].longValue());
			for (var v : values) {
				long l = Math.abs(v.longValue());
				if (l > maxAbs) maxAbs = l;
			}
			int encodingLength;
			if (maxAbs < (hasNegative ? 0x80 : 0x100)) encodingLength = 0;
			else if (maxAbs < (hasNegative ? 0x8000 : 0x10000)) encodingLength = 1;
			else if (maxAbs < (hasNegative ? 0x800000 : 0x1000000)) encodingLength = 2;
			else if (maxAbs < (hasNegative ? 0x80000000L : 0x100000000L)) encodingLength = 3;
			else if (maxAbs < (hasNegative ? 0x8000000000L : 0x10000000000L)) encodingLength = 4;
			else if (maxAbs < (hasNegative ? 0x800000000000L : 0x1000000000000L)) encodingLength = 5;
			else if (maxAbs < (hasNegative ? 0x80000000000000L : 0x100000000000000L)) encodingLength = 6;
			else encodingLength = 7;
			bits.encode((encodingLength & 1) != 0);
			bits.encode((encodingLength & 2) != 0);
			bits.encode((encodingLength & 4) != 0);
			
			bits.close();
			encodeSplitNumbers(out, values, encodingLength, hasNegative, 1L << (encodingLength * 8 + 7));
			/*
			encodeSplitBitsNumbers(bits, values, encodingLength, hasNegative, 1L << (encodingLength * 8 + 7));
			bits.close();
			*/
		}

		private static void decodeTime(InputStream in, Long[] values) throws IOException {
			BitDecoder bits = new BitDecoder(in);
			
			boolean hasNull = bits.decode();
			boolean[] isNull = new boolean[values.length];
			boolean allNull = hasNull;
			if (hasNull) {
				for (int i = 0; i < values.length; ++i) {
					isNull[i] = bits.decode();
					allNull = allNull && isNull[i];
				}
			}
			if (allNull) return;
			
			boolean hasNegative = bits.decode();
			int addLastDigit = 0;
			for (int i = 0; i < 3; ++i) {
				boolean b = bits.decode();
				if (b) break;
				addLastDigit++;
			}

			int encodingLength = (bits.decode() ? 1 : 0) | (bits.decode() ? 2 : 0) | (bits.decode() ? 4 : 0);

			decodeSplitNumbers(in, values, encodingLength, hasNegative, isNull, addLastDigit, 1L << (encodingLength * 8 + 7));
			//decodeSplitBitsNumbers(bits, values, encodingLength, hasNegative, isNull, addLastDigit, 1L << (encodingLength * 8 + 7));
		}

		private static void encodeAccuracy(OutputStream out, Long[] values) throws IOException {
			// posAccuracy, with factor 100: 10km would be 1000000 (0xF4240) => 24 bits integer
			// eleAccuracy, with factor 100: same 24 bits integer

			BitEncoder bits = new BitEncoder(out);
			
			int nbNull = 0;
			for (var v : values) if (v == null) nbNull++;
			if (nbNull == 0) {
				bits.encode(false);
			} else {
				bits.encode(true);
				for (var v : values) bits.encode(v == null);
				Long[] newValues = new Long[values.length - nbNull];
				int pos = 0;
				for (int i = 0; i < values.length; ++i) if (values[i] != null) newValues[pos++] = values[i];
				values = newValues;
			}
			if (values.length == 0) {
				bits.close();
				return;
			}
			boolean hasNegative = false;
			for (long l : values) if (l < 0) { hasNegative = true; break; }
			bits.encode(hasNegative);
			
			for (int i = 0; i < 2; ++i) {
				boolean hasLastDigit = false;
				for (long l : values) if ((l % 10) != 0) { hasLastDigit = true; break; }
				bits.encode(hasLastDigit);
				if (hasLastDigit) break;
				for (int j = 0; j < values.length; ++j) values[j] = values[j] / 10;
			}
			
			long maxAbs = Math.abs(values[0].longValue());
			for (var v : values) {
				long l = Math.abs(v.longValue());
				if (l > maxAbs) maxAbs = l;
			}
			int encodingLength;
			if (maxAbs < (hasNegative ? 0x80 : 0x100)) encodingLength = 0;
			else if (maxAbs < (hasNegative ? 0x8000 : 0x10000)) encodingLength = 1;
			else encodingLength = 2;
			bits.encode((encodingLength & 1) != 0);
			bits.encode((encodingLength & 2) != 0);
			/*
			bits.close();
			encodeSplitNumbers(out, values, encodingLength, hasNegative, 1L << (encodingLength * 8 + 7));
			*/
			encodeSplitBitsNumbers(bits, values, encodingLength, hasNegative, 1L << (encodingLength * 8 + 7));
			bits.close();
		}

		private static void decodeAccuracy(InputStream in, Long[] values) throws IOException {
			BitDecoder bits = new BitDecoder(in);
			
			boolean hasNull = bits.decode();
			boolean[] isNull = new boolean[values.length];
			boolean allNull = hasNull;
			if (hasNull) {
				for (int i = 0; i < values.length; ++i) {
					isNull[i] = bits.decode();
					allNull = allNull && isNull[i];
				}
			}
			if (allNull) return;

			boolean hasNegative = bits.decode();
			int addLastDigit = 0;
			for (int i = 0; i < 2; ++i) {
				boolean b = bits.decode();
				if (b) break;
				addLastDigit++;
			}

			int encodingLength = (bits.decode() ? 1 : 0) | (bits.decode() ? 2 : 0);
			
			//decodeSplitNumbers(in, values, encodingLength, hasNegative, isNull, addLastDigit, 1L << (encodingLength * 8 + 7));
			decodeSplitBitsNumbers(bits, values, encodingLength, hasNegative, isNull, addLastDigit, 1L << (encodingLength * 8 + 7));
		}
		
		private static class BitEncoder {
			private OutputStream out;
			private int currentByte = 0;
			private int currentMask = 1;
			
			private BitEncoder(OutputStream out) {
				this.out = out;
			}
			
			private void encode(boolean bit) throws IOException {
				if (bit) currentByte |= currentMask;
				currentMask <<= 1;
				if (currentMask == 0x100) {
					out.write(currentByte);
					currentByte = 0;
					currentMask = 1;
				}
			}
			
			private void encodeNumber(int number, int nbBits) throws IOException {
				for (int i = 0; i < nbBits; ++i) {
					encode(((number >> i) & 1) != 0);
				}
			}
			
			private void close() throws IOException {
				if (currentMask > 1) {
					out.write(currentByte);
				}
			}
		}
		
		private static class BitDecoder {
			private InputStream in;
			private int currentMask = 0x100;
			private int currentByte = 0;
			
			private BitDecoder(InputStream in) {
				this.in = in;
			}
			
			private boolean decode() throws IOException {
				if (currentMask == 0x100) {
					currentByte = in.read();
					currentMask = 1;
				}
				boolean bit = (currentByte & currentMask) != 0;
				currentMask <<= 1;
				return bit;
			}
			
			private int decodeNumber(int nbBits) throws IOException {
				int n = 0;
				for (int i = 0; i < nbBits; ++i) {
					if (decode())
						n |= 1 << i;
				}
				return n;
			}
		}
		
		private static class DigitEncoder {
			private AccessibleByteArrayOutputStream out = new AccessibleByteArrayOutputStream(1024);
			private long currentValue = 0;
			private long currentMask = 1;
			
			private void encode(int digit) {
				currentValue += (digit - 1) * currentMask;
				if (currentMask == 150094635296999121L) {
					flush();
				} else {
					currentMask *= 9;
				}
			}
			
			private void flush() {
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
			
			private void close(OutputStream o) throws IOException{
				if (currentMask > 1) flush();
				o.write(out.getData(), 0, out.getLength());
			}
		}
		
		private static class DigitDecoder {
			private InputStream in;
			private long currentValue = 0;
			private long currentMask = 1;
			private DigitDecoder(InputStream in) {
				this.in = in;
			}
			private int decode() throws IOException {
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
		
		private static void encodeString(OutputStream out, String s) throws IOException {
			if (s == null || s.isEmpty()) {
				encodeInteger2(0, out);
				return;
			}
			var bytes = s.getBytes(StandardCharsets.UTF_8);
			encodeInteger2(bytes.length, out);
			out.write(bytes);
		}
		
		private static String decodeString(InputStream in) throws IOException {
			int l = decodeInteger2(in);
			if (l == 0) return "";
			byte[] bytes = new byte[l];
			IOUtils.readFully(in, bytes);
			return new String(bytes, StandardCharsets.UTF_8);
		}
		
		private static void encodeStringMap(OutputStream out, Map<String, String> map) throws IOException {
			if (map == null || map.isEmpty()) {
				encodeInteger2(0, out);
				return;
			}
			encodeInteger2(map.size(), out);
			for (var entry : map.entrySet()) {
				encodeString(out, entry.getKey());
				encodeString(out, entry.getValue());
			}
		}
		
		private static Map<String, String> decodeStringMap(InputStream in) throws IOException {
			int size = decodeInteger2(in);
			if (size == 0) return null;
			Map<String, String> map = new HashMap<>();
			for (int i = 0; i < size; ++i) {
				String key = decodeString(in);
				String value = decodeString(in);
				map.put(key, value);
			}
			return map;
		}
		
		private static long toUnsigned(long v) {
			return v < 0 ? ((-v) << 1) | 1 : (v << 1);
		}
		
		private static long toSigned(long v) {
			long abs = v >> 1;
			if ((v & 1) == 0) return abs;
			return -abs;
		}
		
		private static long encode24bitsNullable(Long v) {
			if (v == null) return 0;
			if (v > 0x7FFFFE) return 0x7FFFFFF;
			if (v >= 0) return v + 1;
			if (v < -0x7FFFFF) return -0x7FFFFF;
			return v;
		}
		
		private static Long decode24bitsNullable(long v) {
			if (v == 0) return null;
			if (v > 0) return Long.valueOf(v - 1);
			return Long.valueOf(v);
		}
		
		private static long encode32bitsNullable(Long v) {
			if (v == null) return 0;
			if (v > 0x7FFFFFFE) return 0x7FFFFFFFFL;
			if (v >= 0) return v + 1;
			if (v < -0x7FFFFFFF) return -0x7FFFFFFFL;
			return v;
		}
		
		private static Long decode32bitsNullable(long v) {
			if (v == 0) return null;
			if (v > 0) return Long.valueOf(v - 1);
			return Long.valueOf(v);
		}
		
		private static long encode64bitsNullable(Long v) {
			if (v == null) return 0;
			long l = v.longValue();
			if (l == Long.MAX_VALUE) return v;
			if (l >= 0) return l + 1;
			return l;
		}
		
		private static Long decode64bitsNullable(long v) {
			if (v == 0) return null;
			if (v > 0) return Long.valueOf(v - 1);
			return Long.valueOf(v);
		}
		
	}
	
}
