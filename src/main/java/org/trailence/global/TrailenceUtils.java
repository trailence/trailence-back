package org.trailence.global;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TrailenceUtils {
	
	public static ObjectMapper mapper = new ObjectMapper();

	public static String hashPassword(String password) {
		return DigestUtils.sha256Hex(password);
	}
	
	public static <T> List<T> merge(List<T> list1, List<T> list2) {
		List<T> result = new ArrayList<>(list1.size() + list2.size());
		result.addAll(list1);
		result.addAll(list2);
		return result;
	}
	
}
