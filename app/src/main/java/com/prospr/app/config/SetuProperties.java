package com.prospr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "setu")
@Getter
@Setter
public class SetuProperties {

    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private String productInstanceId;
	public String getBaseUrl() {
		return baseUrl;
	}
	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}
	public String getClientId() {
		return clientId;
	}
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
	public String getClientSecret() {
		return clientSecret;
	}
	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}
	public String getProductInstanceId() {
		return productInstanceId;
	}
	public void setProductInstanceId(String productInstanceId) {
		this.productInstanceId = productInstanceId;
	}

}
