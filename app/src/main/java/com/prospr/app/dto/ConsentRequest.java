package com.prospr.app.dto;

import lombok.Data;

@Data
public class ConsentRequest {
	private String mobile;

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}
}
