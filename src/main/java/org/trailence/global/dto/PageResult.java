package org.trailence.global.dto;

import java.util.List;

import org.springframework.data.domain.Pageable;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PageResult<T> {

	private int page;
	private int size;
	private long count;
	private List<T> elements;
	
	public PageResult(Pageable pageable, List<T> elements, long count) {
		this.count = count;
		this.elements = elements;
		if (pageable.isPaged()) {
			this.page = pageable.getPageNumber();
			this.size = pageable.getPageSize();
		} else {
			this.page = 0;
			this.size = -1;
		}
	}
	
}
