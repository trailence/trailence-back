package org.trailence.global.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

	private int httpCode;
	private String errorCode;
	private String errorMessage;
	
}
