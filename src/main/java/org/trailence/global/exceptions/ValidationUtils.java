package org.trailence.global.exceptions;

import java.util.Collection;
import java.util.UUID;

import org.springframework.util.function.ThrowingConsumer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationUtils {
	
	public static final String INVALID_PREFIX = "invalid-";
	public static final String MISSING_PREFIX = "missing-";
	
	@SuppressWarnings("java:S1452")
	public static <T> Field<T, ?> field(String name, T value) {
		return new Field<>(name, value);
	}
	
	public static FieldString field(String name, String value) {
		return new FieldString(name, value);
	}
	
	@RequiredArgsConstructor
	@SuppressWarnings("unchecked")
	public static class Field<T, S extends Field<T, S>> {
		
		protected final String name;
		protected final T value;
		
		public S notNull() {
			if (value == null) throw new BadRequestException(MISSING_PREFIX + name, name + " cannot be null");
			return (S) this;
		}
		
		public S notEqualTo(T forbiddenValue) {
			if (forbiddenValue.equals(value)) throw new BadRequestException(INVALID_PREFIX + name + "-value", name + " cannot be " + value);
			return (S) this;
		}
		
		public S notIn(Collection<T> forbiddenValues) {
			if (forbiddenValues.contains(value)) throw new BadRequestException(INVALID_PREFIX + name + "-value", name + " cannot be " + value);
			return (S) this;
		}
		
		public S valid(ThrowingConsumer<T> validation) {
			try {
				validation.accept(value);
			} catch (Exception e) {
				throw new BadRequestException(INVALID_PREFIX + name, "Invalid " + name + " (" + e.getMessage() + ")");
			}
			return (S) this;
		}
		
		@SuppressWarnings("java:S1452")
		public Field<T, ?> nullable() {
			if (value == null) return new Valid<>(name, value);
			return this;
		}
		
		public static class Valid<T> extends Field<T, Valid<T>> {
			private Valid(String name, T value) { super(name, value); }
			
			@Override
			public Valid<T> notNull() { return this; }
			
			@Override
			public Valid<T> notEqualTo(T forbiddenValue) { return this; }
			
			@Override
			public Valid<T> valid(ThrowingConsumer<T> validation) { return this; }
		}
		
	}
	
	public static class FieldString extends Field<String, FieldString> {
		
		public FieldString(String name, String value) {
			super(name, value);
		}
		
		public FieldString maxLength(int max) {
			if (value.length() > max) throw new BadRequestException(INVALID_PREFIX + name + "-too-long", name + " exceeds the maximum length of " + max + " characters");
			return this;
		}
		
		public FieldString isUuid() {
			try {
				UUID.fromString(value);
			} catch (IllegalArgumentException e) {
				throw new BadRequestException(INVALID_PREFIX + name, "Invalid " + name + ": " + value + " is not a valid UUID");
			}
			return this;
		}
		
		@Override
		public FieldString nullable() {
			if (value == null) return new Valid(name, value);
			return this;
		}
		
		public static class Valid extends FieldString {
			private Valid(String name, String value) {
				super(name, value);
			}
			
			@Override
			public Valid notNull() { return this; }
			
			@Override
			public Valid notEqualTo(String forbiddenValue) { return this; }
			
			@Override
			public Valid valid(ThrowingConsumer<String> validation) { return this; }
			
			@Override
			public FieldString maxLength(int max) { return this; }
			
			@Override
			public FieldString isUuid() { return this; }
		}
		
	}
}
