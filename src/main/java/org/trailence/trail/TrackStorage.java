package org.trailence.trail;

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
			
			public PointOffsets getPointOffsets() {
				PointOffsets o = new PointOffsets();
				o.latitude = new int[4];
				o.longitude = new int[4];
				if (hasElevation) o.elevation = new int[3];
				if (hasTime) o.time = new int[8];
				if (hasPa) o.pa = new int[3];
				if (hasEa) o.ea = new int[3];
				
				// latitude and longitude expect 2 first bytes to be 0
				// elevation expect 2 first bytes to be 0
				// posAccuracy expect first byte to be 0
				// eleAccuracy expect first byte to be 0, second byte most of time
				// time expect 6 first bytes to be 0
				int pos = 0;
				o.latitude[0] = pos++;
				o.longitude[0] = pos++;
				if (hasElevation) o.elevation[0] = pos++;
				if (hasTime) o.time[0] = pos++;
				if (hasPa) o.pa[0] = pos++;
				if (hasEa) o.ea[0] = pos++;
				o.latitude[1] = pos++;
				o.longitude[1] = pos++;
				if (hasTime) {
					o.time[1] = pos++;
					o.time[2] = pos++;
					o.time[3] = pos++;
					o.time[4] = pos++;
				}
				if (hasElevation) o.elevation[1] = pos++;
				if (hasEa) o.ea[1] = pos++;
				if (hasPa) o.pa[1] = pos++;
				o.latitude[2] = pos++;
				o.longitude[2] = pos++;
				if (hasTime) o.time[5] = pos++;
				if (hasEa) o.ea[2] = pos++;
				if (hasPa) o.pa[2] = pos++;
				if (hasElevation) o.elevation[2] = pos++;
				o.latitude[3] = pos++;
				o.longitude[3] = pos++;
				if (hasTime) {
					o.time[6] = pos++;
					o.time[7] = pos++;
				}
				o.nbBytes = pos;
				return o;
			}
		}
		
		private static class PointOffsets {
			int nbBytes;
			int[] latitude;
			int[] longitude;
			int[] elevation;
			int[] time;
			int[] pa;
			int[] ea;
		}
		
		
		public static byte[] v1DtoToV2(V1.StoredData value) throws IOException {
			try (AccessibleByteArrayOutputStream bos = new AccessibleByteArrayOutputStream(8192)) {
				Deflater def = new Deflater(Deflater.BEST_COMPRESSION, true);
				try (DeflaterOutputStream gos = new DeflaterOutputStream(bos, def)) {
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
					info.encode(gos);
					for (var segment : value.s) {
						encodeInteger2to4(segment.getP().length, gos);
					}
					PointOffsets po = info.getPointOffsets();
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
						byte[] bytes = new byte[nb * po.nbBytes];
						for (int i = 0; i < nb; ++i) {
							Long v = points[i].getL();
							long l = v == null ? 0 : v.longValue();
							if (l < -900000000 || l > 900000000) throw new IOException("Invalid latitude: " + l);
							l = toUnsigned(l);
							encodePoint32Bits(bytes, nb, i, l, po.latitude);
							
							v = points[i].getN();
							l = v == null ? 0 : v.longValue();
							if (l < -1800000000 || l > 1800000000) throw new IOException("Invalid longitude: " + l);
							l = toUnsigned(l);
							encodePoint32Bits(bytes, nb, i, l, po.longitude);
							
							if (info.hasElevation) {
								v = points[i].getE();
								l = toUnsigned(encode24bitsNullable(v));
								encodePoint24Bits(bytes, nb, i, l, po.elevation);
							}
							
							if (info.hasTime) {
								v = points[i].getT();
								l = encode64bitsNullable(v);
								encodePoint64Bits(bytes, nb, i, l, po.time);
							}
							
							if (info.hasPa) {
								v = points[i].getPa();
								l = toUnsigned(encode24bitsNullable(v));
								encodePoint24Bits(bytes, nb, i, l, po.pa);
							}
							
							if (info.hasEa) {
								v = points[i].getEa();
								l = toUnsigned(encode24bitsNullable(v));
								encodePoint24Bits(bytes, nb, i, l, po.ea);
							}
						}
						gos.write(bytes);
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
						gos.write(bytes);

						for (var wp : value.wp) {
							encodeString(gos, wp.getNa());
							encodeString(gos, wp.getDe());
							encodeStringMap(gos, wp.getNt());
							encodeStringMap(gos, wp.getDt());
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
				try (InflaterInputStream gis = new InflaterInputStream(bis, inf)) {
					Info info = Info.decode(gis);
					Track.Point[][] segments = new Track.Point[info.nbSegments][];
					for (int i = 0; i < info.nbSegments; ++i) {
						segments[i] = new Track.Point[decodeInteger2to4(gis)];
					}
					PointOffsets po = info.getPointOffsets();
					for (int s = 0; s < info.nbSegments; ++s) {
						int nb = segments[s].length;
						byte[] bytes = new byte[nb * po.nbBytes];
						IOUtils.readFully(gis, bytes);
						
						for (int i = 0; i < nb; ++i) {
							long lat = toSigned(decodePoint32Bits(bytes, nb, i, po.latitude));
							long lon = toSigned(decodePoint32Bits(bytes, nb, i, po.longitude));
							Long ele = info.hasElevation ? decode24bitsNullable(toSigned(decodePoint24Bits(bytes, nb, i, po.elevation))) : null;
							Long tim = info.hasTime ? decode64bitsNullable(decodePoint64Bits(bytes, nb, i, po.time)) : null;
							Long pa = info.hasPa ? decode24bitsNullable(toSigned(decodePoint24Bits(bytes, nb, i, po.pa))) : null;
							Long ea = info.hasEa ? decode24bitsNullable(toSigned(decodePoint24Bits(bytes, nb, i, po.ea))) : null;
							segments[s][i] = new Track.Point(lat == 0 ? null : lat, lon == 0 ? null : lon, ele, tim, pa, ea, null, null);
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
						IOUtils.readFully(gis, bytes);
						for (int i = 0; i < nb; ++i) {
							Long lat = decode32bitsNullable(toSigned(decodePoint32Bits(bytes, nb, i, new int[] { 4, 6, 8, 10 })));
							Long lon = decode32bitsNullable(toSigned(decodePoint32Bits(bytes, nb, i, new int[] { 5, 7, 9, 11 })));
							Long ele = decode24bitsNullable(toSigned(decodePoint24Bits(bytes, nb, i, new int[] { 0, 3, 12 })));
							Long tim = decode64bitsNullable(decodePoint64Bits(bytes, nb, i, new int[] { 1, 2, 13, 14, 15, 16, 17, 18 }));
							v1.wp[i] = new Track.WayPoint(lat, lon, ele, tim, null, null, null, null);
						}
						for (int i = 0; i < nb; ++i) {
							v1.wp[i].setNa(decodeString(gis));
							v1.wp[i].setDe(decodeString(gis));
							v1.wp[i].setNt(decodeStringMap(gis));
							v1.wp[i].setDt(decodeStringMap(gis));
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
				((bytes[offset[0] * nbPoints + pointIndex] & 0xFF) << 24);
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
