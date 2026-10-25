package com.prospr.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AppApplication {

	public static void main(String[] args) {
		logDbEnvFingerprint(); // TEMP: remove once the Render DB login issue is resolved.
		SpringApplication.run(AppApplication.class, args);
	}

	/** Prints what the process actually received for the DB_* variables without revealing the password:
	 *  length, stray whitespace/quotes, and a short hash to compare between Render and a local run. */
	private static void logDbEnvFingerprint() {
		for (String name : new String[] {"DB_URL", "DB_USERNAME", "DB_PASSWORD"}) {
			String value = System.getenv(name);
			if (value == null) {
				System.out.println("[db-diag] " + name + " = <NOT SET>");
				continue;
			}
			boolean secret = name.equals("DB_PASSWORD");
			System.out.println("[db-diag] " + name
					+ " length=" + value.length()
					+ " hasOuterWhitespace=" + !value.equals(value.strip())
					+ " hasQuotes=" + (value.startsWith("\"") || value.startsWith("'") || value.endsWith("\"") || value.endsWith("'"))
					+ (secret ? " sha256[0..8]=" + shortHash(value) : " value=" + value.replaceAll("(?<=//)[^/@]*@", "")));
		}
	}

	private static String shortHash(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest).substring(0, 8);
		} catch (Exception ex) {
			return "n/a";
		}
	}

}
