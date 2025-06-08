package org.trailence.trail;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.apache.commons.lang3.tuple.Pair;
import org.trailence.global.AccessibleByteArrayOutputStream;
import org.trailence.trail.dto.Track;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TrackEncoding {
	
	public static byte[] encode(Track.Segment[] segments, Track.WayPoint[] waypoints) throws IOException {
		try (AccessibleByteArrayOutputStream encoded = new AccessibleByteArrayOutputStream(8192)) {
			encode(segments, encoded);
			encode(waypoints, encoded);
			try (AccessibleByteArrayOutputStream bos = new AccessibleByteArrayOutputStream(8192)) {
				Deflater d = new Deflater(9);
				try (DeflaterOutputStream zos = new DeflaterOutputStream(bos, d)) {
					zos.write(encoded.getData(), 0, encoded.getLength());
				} finally {
					d.end();
				}
				byte[] compressed = bos.getData();
				int len = bos.getLength();
				if (len == compressed.length) return compressed;
				byte[] result = new byte[len];
				System.arraycopy(compressed, 0, result, 0, len);
				return result;
			}
		}
	}
	
	public static Pair<Track.Segment[], Track.WayPoint[]> decode(byte[] encoded) throws IOException {
		try (
			ByteArrayInputStream bis = new ByteArrayInputStream(encoded);
			InflaterInputStream gis = new InflaterInputStream(bis);
			BufferedInputStream buf = new BufferedInputStream(gis)
		) {
			Track.Segment[] segments = decodeSegments(buf);
			Track.WayPoint[] waypoints = decodeWaypoints(buf);
			return Pair.of(segments, waypoints);
		}
	}

	private static void encode(Track.Segment[] segments, OutputStream out) throws IOException {
		int l = segments != null ? segments.length : 0;
		encodeUnsigned16bits(l, out);
		for (int i = 0; i < l; ++i)
			encode(segments[i].getP(), out);
	}
	
	private static Track.Segment[] decodeSegments(InputStream in) throws IOException {
		int l = decodeUnsigned16bits(in);
		var segments = new Track.Segment[l];
		for (int i = 0; i < l; ++i) {
			segments[i] = new Track.Segment();
			segments[i].setP(decodePoints(in));
		}
		return segments;
	}
	
	private static void encode(Track.Point[] points, OutputStream out) throws IOException {
		int l = points != null ? points.length : 0;
		encodeUnsigned32bits(l, out);
		for (int i = 0; i < l; ++i)
			encode(points[i].getL(), out);
		for (int i = 0; i < l; ++i)
			encode(points[i].getN(), out);
		for (int i = 0; i < l; ++i)
			encode(points[i].getE(), out);
		for (int i = 0; i < l; ++i)
			encode(points[i].getT(), out);
		for (int i = 0; i < l; ++i)
			encode(points[i].getPa(), out);
		for (int i = 0; i < l; ++i)
			encode(points[i].getEa(), out);
		for (int i = 0; i < l; ++i)
			encode(points[i].getH(), out);
		for (int i = 0; i < l; ++i)
			encode(points[i].getS(), out);
	}
	
	private static Track.Point[] decodePoints(InputStream in) throws IOException {
		int l = decodeUnsigned32bits(in);
		var points = new Track.Point[l];
		for (int i = 0; i < l; ++i) {
			points[i] = new Track.Point();
			points[i].setL(decodeLong(in));
		}
		for (int i = 0; i < l; ++i)
			points[i].setN(decodeLong(in));
		for (int i = 0; i < l; ++i)
			points[i].setE(decodeLong(in));
		for (int i = 0; i < l; ++i)
			points[i].setT(decodeLong(in));
		for (int i = 0; i < l; ++i)
			points[i].setPa(decodeLong(in));
		for (int i = 0; i < l; ++i)
			points[i].setEa(decodeLong(in));
		for (int i = 0; i < l; ++i)
			points[i].setH(decodeLong(in));
		for (int i = 0; i < l; ++i)
			points[i].setS(decodeLong(in));
		return points;
	}
	
	private static void encode(Track.WayPoint[] waypoints, OutputStream out) throws IOException {
		int l = waypoints != null ? waypoints.length : 0;
		encodeUnsigned16bits(l, out);
		for (int i = 0; i < l; ++i) {
			var wp = waypoints[i];
			encode(wp.getN(), out);
			encode(wp.getL(), out);
			encode(wp.getE(), out);
			encode(wp.getT(), out);
			encode(wp.getNa(), 2, out);
			encode(wp.getDe(), 3, out);
		}
	}
	
	private static Track.WayPoint[] decodeWaypoints(InputStream in) throws IOException {
		int l = decodeUnsigned16bits(in);
		var waypoints = new Track.WayPoint[l];
		for (int i = 0; i < l; ++i) {
			var wp = new Track.WayPoint();
			wp.setN(decodeLong(in));
			wp.setL(decodeLong(in));
			wp.setE(decodeLong(in));
			wp.setT(decodeLong(in));
			wp.setNa(decodeString(in, 2));
			wp.setDe(decodeString(in, 3));
			waypoints[i] = wp;
		}
		return waypoints;
	}
	
	private static void encodeUnsigned16bits(int v, OutputStream out) throws IOException {
		out.write(v & 0xFF);
		out.write((v & 0xFF00) >> 8);
	}
	
	private static int decodeUnsigned16bits(InputStream in) throws IOException {
		int b1 = readUnsigned8bits(in);
		int b2 = readUnsigned8bits(in);
		return (b1 & 0xFF) | ((b2 & 0xFF) << 8);
	}
	
	private static int readUnsigned8bits(InputStream in) throws IOException {
		int b = in.read();
		if (b < 0) throw new EOFException();
		return b;
	}

	private static void encodeUnsigned32bits(int v, OutputStream out) throws IOException {
		out.write(v & 0xFF);
		out.write((v & 0xFF00) >> 8);
		out.write((v & 0xFF0000) >> 16);
		out.write((v & 0xFF000000) >> 24);
	}
	
	private static int decodeUnsigned32bits(InputStream in) throws IOException {
		int b1 = readUnsigned8bits(in);
		int b2 = readUnsigned8bits(in);
		int b3 = readUnsigned8bits(in);
		int b4 = readUnsigned8bits(in);
		return (b1 & 0xFF) | ((b2 & 0xFF) << 8) | ((b3 & 0xFF) << 16) | ((b4 & 0xFF) << 24);
	}
	
	private static void encodeUnsigned(int v, int nbBytes, OutputStream out) throws IOException {
		for (int i = 0; i < nbBytes; ++i) {
			out.write(v & 0xFF);
			v >>= 8;
		}
	}
	
	private static int decodeUnsigned(int nbBytes, InputStream in) throws IOException {
		int v = 0;
		int shift = 0;
		for (int i = 0; i < nbBytes; ++i) {
			v = v | (readUnsigned8bits(in) << shift);
			shift += 8;
		}
		return v;
	}

	private static void encode(Long value, OutputStream out) throws IOException {
		// bit 0: 1 means null
		// bit 1: 1 means negative value
		// bit 2..5: number of bytes following
		if (value == null) {
			out.write(0x01);
			return;
		}
		int b1 = 0;
		long v = value.longValue();
		if (v < 0) {
			b1 = 0x02;
			v = -v;
		}
		b1 |= (v & 0x3) << 6;
		v >>= 2;
		if (v == 0) {
			out.write(b1);
			return;
		}
		int nbBytes = 0;
		if (v < 0x100) nbBytes = 1;
		else if (v < 0x10000) nbBytes = 2;
		else if (v < 0x1000000) nbBytes = 3;
		else if (v < 0x100000000L) nbBytes = 4;
		else if (v < 0x10000000000L) nbBytes = 5;
		else if (v < 0x1000000000000L) nbBytes = 6;
		else if (v < 0x100000000000000L) nbBytes = 7;
		else nbBytes = 8;
		b1 |= nbBytes << 2;
		out.write(b1);
		for (int i = 0; i < nbBytes; ++i) {
			out.write((byte)(v & 0xFF));
			v >>= 8;
		}
	}
	
	private static Long decodeLong(InputStream in) throws IOException {
		int b1 = readUnsigned8bits(in);
		if ((b1 & 0x01) != 0) return null;
		boolean negative = (b1 & 0x02) != 0;
		int nbBytes = (b1 >> 2) & 0xF;
		long v = b1 >> 6;
		int shift = 2;
		for (int i = 0; i < nbBytes; ++i) {
			int b = readUnsigned8bits(in);
			v = v | (((long)b) << shift);
			shift += 8;
		}
		if (negative) v = -v;
		return Long.valueOf(v);
	}
	
	private static void encode(String s, int nbBytes, OutputStream out) throws IOException {
		if (s == null) {
			for (int i = 0; i < nbBytes; ++i)
				out.write(0);
			return;
		}
		byte[] b = s.getBytes(StandardCharsets.UTF_8);
		int l = b.length + 1;
		int max = 0xFF;
		for (int i = 1; i < nbBytes; ++i) max = (max << 8) | 0xFF;
		if (l > max) l = max;
		encodeUnsigned(l, nbBytes, out);
		out.write(b, 0, l - 1);
	}
	
	private static String decodeString(InputStream in, int nbBytes) throws IOException {
		int l = decodeUnsigned(nbBytes, in);
		if (l == 0) return null;
		byte[] b = new byte[l - 1];
		int nb = in.readNBytes(b, 0, l - 1);
		if (nb < l - 1) throw new EOFException();
		return new String(b, StandardCharsets.UTF_8);
	}

}
