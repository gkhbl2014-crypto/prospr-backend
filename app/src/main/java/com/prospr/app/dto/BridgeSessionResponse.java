package com.prospr.app.dto;



import lombok.Data;

@Data
public class BridgeSessionResponse {

    private String url;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
