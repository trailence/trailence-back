package org.trailence.global;

import java.io.ByteArrayOutputStream;

public class AccessibleByteArrayOutputStream extends ByteArrayOutputStream {

	public AccessibleByteArrayOutputStream(int initialCapacity) {
		super(initialCapacity);
	}
	
	public byte[] getData() {
		return this.buf;
	}
	
	public int getLength() {
		return this.count;
	}
	
}
