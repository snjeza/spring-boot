/*
 * Copyright 2012-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.security.oauth2.resource.servlet;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import jakarta.servlet.Filter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link OAuth2ResourceServerAutoConfiguration}.
 *
 * @author Madhura Bhave
 * @author Artsiom Yudovin
 * @author HaiTao Zhang
 * @author Mushtaq Ahmed
 */
class OAuth2ResourceServerAutoConfigurationTests {

	private WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(OAuth2ResourceServerAutoConfiguration.class))
			.withUserConfiguration(TestConfig.class);

	private MockWebServer server;

	private static final String JWK_SET = "{\"keys\":[{\"kty\":\"RSA\",\"e\":\"AQAB\",\"use\":\"sig\","
			+ "\"kid\":\"one\",\"n\":\"oXJ8OyOv_eRnce4akdanR4KYRfnC2zLV4uYNQpcFn6oHL0dj7D6kxQmsXoYgJV8ZVDn71KGm"
			+ "uLvolxsDncc2UrhyMBY6DVQVgMSVYaPCTgW76iYEKGgzTEw5IBRQL9w3SRJWd3VJTZZQjkXef48Ocz06PGF3lhbz4t5UEZtd"
			+ "F4rIe7u-977QwHuh7yRPBQ3sII-cVoOUMgaXB9SHcGF2iZCtPzL_IffDUcfhLQteGebhW8A6eUHgpD5A1PQ-JCw_G7UOzZAj"
			+ "jDjtNM2eqm8j-Ms_gqnm4MiCZ4E-9pDN77CAAPVN7kuX6ejs9KBXpk01z48i9fORYk9u7rAkh1HuQw\"}]}";

	@AfterEach
	void cleanup() throws Exception {
		if (this.server != null) {
			this.server.shutdown();
		}
	}

	@Test
	void autoConfigurationShouldConfigureResourceServer() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com")
				.run((context) -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					assertThat(getBearerTokenFilter(context)).isNotNull();
				});
	}

	@Test
	void autoConfigurationShouldMatchDefaultJwsAlgorithm() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com")
				.run((context) -> {
					JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
					assertThat(jwtDecoder).extracting("jwtProcessor.jwsKeySelector.jwsAlgs")
							.asInstanceOf(InstanceOfAssertFactories.collection(JWSAlgorithm.class))
							.containsExactlyInAnyOrder(JWSAlgorithm.RS256);
				});
	}

	@Test
	void autoConfigurationShouldConfigureResourceServerWithSingleJwsAlgorithm() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com",
						"spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS384")
				.run((context) -> {
					JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
					assertThat(jwtDecoder).extracting("jwtProcessor.jwsKeySelector.jwsAlgs")
							.asInstanceOf(InstanceOfAssertFactories.collection(JWSAlgorithm.class))
							.containsExactlyInAnyOrder(JWSAlgorithm.RS384);
					assertThat(getBearerTokenFilter(context)).isNotNull();
				});
	}

	@Test
	void autoConfigurationShouldConfigureResourceServerWithMultipleJwsAlgorithms() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com",
						"spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256, RS384, RS512")
				.run((context) -> {
					JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
					assertThat(jwtDecoder).extracting("jwtProcessor.jwsKeySelector.jwsAlgs")
							.asInstanceOf(InstanceOfAssertFactories.collection(JWSAlgorithm.class))
							.containsExactlyInAnyOrder(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512);
					assertThat(getBearerTokenFilter(context)).isNotNull();
				});
	}

	@Test
	void autoConfigurationUsingPublicKeyValueShouldConfigureResourceServerUsingSingleJwsAlgorithm() {
		this.contextRunner.withPropertyValues(
				"spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:public-key-location",
				"spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS384").run((context) -> {
					NimbusJwtDecoder nimbusJwtDecoder = context.getBean(NimbusJwtDecoder.class);
					assertThat(nimbusJwtDecoder).extracting("jwtProcessor.jwsKeySelector.expectedJWSAlg")
							.isEqualTo(JWSAlgorithm.RS384);
				});
	}

	@Test
	void autoConfigurationUsingPublicKeyValueWithMultipleJwsAlgorithmsShouldFail() {
		this.contextRunner.withPropertyValues(
				"spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:public-key-location",
				"spring.security.oauth2.resourceserver.jwt.jws-algorithms=RSA256,RS384").run((context) -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasRootCauseMessage(
							"Creating a JWT decoder using a public key requires exactly one JWS algorithm but 2 were "
									+ "configured");
				});
	}

	@Test
	@SuppressWarnings("unchecked")
	void autoConfigurationShouldConfigureResourceServerUsingOidcIssuerUri() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String path = "test";
		String issuer = this.server.url(path).toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponse(cleanIssuerPath);
		this.contextRunner.withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=http://"
				+ this.server.getHostName() + ":" + this.server.getPort() + "/" + path).run((context) -> {
					assertThat(context).hasSingleBean(SupplierJwtDecoder.class);
					assertThat(context.containsBean("jwtDecoderByIssuerUri")).isTrue();
					SupplierJwtDecoder supplierJwtDecoderBean = context.getBean(SupplierJwtDecoder.class);
					Supplier<JwtDecoder> jwtDecoderSupplier = (Supplier<JwtDecoder>) ReflectionTestUtils
							.getField(supplierJwtDecoderBean, "jwtDecoderSupplier");
					jwtDecoderSupplier.get();
				});
		// The last request is to the JWK Set endpoint to look up the algorithm
		assertThat(this.server.getRequestCount()).isEqualTo(2);
	}

	@Test
	@SuppressWarnings("unchecked")
	void autoConfigurationShouldConfigureResourceServerUsingOidcRfc8414IssuerUri() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String path = "test";
		String issuer = this.server.url(path).toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponsesWithErrors(cleanIssuerPath, 1);
		this.contextRunner.withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=http://"
				+ this.server.getHostName() + ":" + this.server.getPort() + "/" + path).run((context) -> {
					assertThat(context).hasSingleBean(SupplierJwtDecoder.class);
					assertThat(context.containsBean("jwtDecoderByIssuerUri")).isTrue();
					SupplierJwtDecoder supplierJwtDecoderBean = context.getBean(SupplierJwtDecoder.class);
					Supplier<JwtDecoder> jwtDecoderSupplier = (Supplier<JwtDecoder>) ReflectionTestUtils
							.getField(supplierJwtDecoderBean, "jwtDecoderSupplier");
					jwtDecoderSupplier.get();
				});
		// The last request is to the JWK Set endpoint to look up the algorithm
		assertThat(this.server.getRequestCount()).isEqualTo(3);
	}

	@Test
	@SuppressWarnings("unchecked")
	void autoConfigurationShouldConfigureResourceServerUsingOAuthIssuerUri() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String path = "test";
		String issuer = this.server.url(path).toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponsesWithErrors(cleanIssuerPath, 2);

		this.contextRunner.withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=http://"
				+ this.server.getHostName() + ":" + this.server.getPort() + "/" + path).run((context) -> {
					assertThat(context).hasSingleBean(SupplierJwtDecoder.class);
					assertThat(context.containsBean("jwtDecoderByIssuerUri")).isTrue();
					SupplierJwtDecoder supplierJwtDecoderBean = context.getBean(SupplierJwtDecoder.class);
					Supplier<JwtDecoder> jwtDecoderSupplier = (Supplier<JwtDecoder>) ReflectionTestUtils
							.getField(supplierJwtDecoderBean, "jwtDecoderSupplier");
					jwtDecoderSupplier.get();
				});
		// The last request is to the JWK Set endpoint to look up the algorithm
		assertThat(this.server.getRequestCount()).isEqualTo(4);
	}

	@Test
	void autoConfigurationShouldConfigureResourceServerUsingPublicKeyValue() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String issuer = this.server.url("").toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponse(cleanIssuerPath);
		this.contextRunner
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:public-key-location")
				.run((context) -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					assertThat(getBearerTokenFilter(context)).isNotNull();
				});
	}

	@Test
	void autoConfigurationShouldFailIfPublicKeyLocationDoesNotExist() {
		this.contextRunner
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:does-not-exist")
				.run((context) -> assertThat(context).hasFailed().getFailure()
						.hasMessageContaining("class path resource [does-not-exist]")
						.hasMessageContaining("Public key location does not exist"));
	}

	@Test
	void autoConfigurationShouldFailIfAlgorithmIsInvalid() {
		this.contextRunner
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:public-key-location",
						"spring.security.oauth2.resourceserver.jwt.jws-algorithms=NOT_VALID")
				.run((context) -> assertThat(context).hasFailed().getFailure()
						.hasMessageContaining("signatureAlgorithm cannot be null"));
	}

	@Test
	void autoConfigurationWhenSetUriKeyLocationAndIssuerUriPresentShouldUseSetUri() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer-uri.com",
						"spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:public-key-location",
						"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com")
				.run((context) -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					assertThat(getBearerTokenFilter(context)).isNotNull();
					assertThat(context.containsBean("jwtDecoderByJwkKeySetUri")).isTrue();
					assertThat(context.containsBean("jwtDecoderByIssuerUri")).isFalse();
				});
	}

	@Test
	void autoConfigurationWhenKeyLocationAndIssuerUriPresentShouldUseIssuerUri() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String issuer = this.server.url("").toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponse(cleanIssuerPath);
		this.contextRunner
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.jwt.issuer-uri=http://" + this.server.getHostName() + ":"
								+ this.server.getPort(),
						"spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:public-key-location")
				.run((context) -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					assertThat(getBearerTokenFilter(context)).isNotNull();
					assertThat(context.containsBean("jwtDecoderByIssuerUri")).isTrue();
				});
	}

	@Test
	void autoConfigurationWhenJwkSetUriNullShouldNotFail() {
		this.contextRunner.run((context) -> assertThat(getBearerTokenFilter(context)).isNull());
	}

	@Test
	void jwtDecoderByJwkSetUriIsConditionalOnMissingBean() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com")
				.withUserConfiguration(JwtDecoderConfig.class)
				.run((context) -> assertThat(getBearerTokenFilter(context)).isNotNull());
	}

	@Test
	void jwtDecoderByOidcIssuerUriIsConditionalOnMissingBean() {
		this.contextRunner
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://jwk-oidc-issuer-location.com")
				.withUserConfiguration(JwtDecoderConfig.class)
				.run((context) -> assertThat(getBearerTokenFilter(context)).isNotNull());
	}

	@Test
	void autoConfigurationShouldBeConditionalOnResourceServerClass() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com")
				.withUserConfiguration(JwtDecoderConfig.class)
				.withClassLoader(new FilteredClassLoader(BearerTokenAuthenticationToken.class)).run((context) -> {
					assertThat(context).doesNotHaveBean(OAuth2ResourceServerAutoConfiguration.class);
					assertThat(getBearerTokenFilter(context)).isNull();
				});
	}

	@Test
	void autoConfigurationForJwtShouldBeConditionalOnJwtDecoderClass() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com")
				.withUserConfiguration(JwtDecoderConfig.class)
				.withClassLoader(new FilteredClassLoader(JwtDecoder.class)).run((context) -> {
					assertThat(context).hasSingleBean(OAuth2ResourceServerAutoConfiguration.class);
					assertThat(getBearerTokenFilter(context)).isNull();
				});
	}

	@Test
	void jwtSecurityFilterShouldBeConditionalOnSecurityFilterChainClass() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com")
				.withUserConfiguration(JwtDecoderConfig.class)
				.withClassLoader(new FilteredClassLoader(SecurityFilterChain.class)).run((context) -> {
					assertThat(context).hasSingleBean(OAuth2ResourceServerAutoConfiguration.class);
					assertThat(getBearerTokenFilter(context)).isNull();
				});
	}

	@Test
	void opaqueTokenSecurityFilterShouldBeConditionalOnSecurityFilterChainClass() {
		this.contextRunner
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.opaquetoken.introspection-uri=https://check-token.com",
						"spring.security.oauth2.resourceserver.opaquetoken.client-id=my-client-id",
						"spring.security.oauth2.resourceserver.opaquetoken.client-secret=my-client-secret")
				.withClassLoader(new FilteredClassLoader(SecurityFilterChain.class)).run((context) -> {
					assertThat(context).hasSingleBean(OAuth2ResourceServerAutoConfiguration.class);
					assertThat(getBearerTokenFilter(context)).isNull();
				});
	}

	@Test
	void autoConfigurationWhenJwkSetUriAndIntrospectionUriAvailable() {
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com",
						"spring.security.oauth2.resourceserver.opaquetoken.introspection-uri=https://check-token.com",
						"spring.security.oauth2.resourceserver.opaquetoken.client-id=my-client-id",
						"spring.security.oauth2.resourceserver.opaquetoken.client-secret=my-client-secret")
				.run((context) -> {
					assertThat(context).hasSingleBean(OpaqueTokenIntrospector.class);
					assertThat(context).hasSingleBean(JwtDecoder.class);
					assertThat(getBearerTokenFilter(context))
							.extracting("authenticationManagerResolver.arg$1.providers").asList()
							.hasAtLeastOneElementOfType(JwtAuthenticationProvider.class);
				});
	}

	@Test
	void autoConfigurationWhenIntrospectionUriAvailableShouldConfigureIntrospectionClient() {
		this.contextRunner
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.opaquetoken.introspection-uri=https://check-token.com",
						"spring.security.oauth2.resourceserver.opaquetoken.client-id=my-client-id",
						"spring.security.oauth2.resourceserver.opaquetoken.client-secret=my-client-secret")
				.run((context) -> {
					assertThat(context).hasSingleBean(OpaqueTokenIntrospector.class);
					assertThat(getBearerTokenFilter(context)).isNotNull();
				});
	}

	@Test
	void opaqueTokenIntrospectorIsConditionalOnMissingBean() {
		this.contextRunner
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.opaquetoken.introspection-uri=https://check-token.com")
				.withUserConfiguration(OpaqueTokenIntrospectorConfig.class)
				.run((context) -> assertThat(getBearerTokenFilter(context)).isNotNull());
	}

	@Test
	void autoConfigurationWhenIntrospectionUriAvailableShouldBeConditionalOnClass() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(BearerTokenAuthenticationToken.class))
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.opaquetoken.introspection-uri=https://check-token.com",
						"spring.security.oauth2.resourceserver.opaquetoken.client-id=my-client-id",
						"spring.security.oauth2.resourceserver.opaquetoken.client-secret=my-client-secret")
				.run((context) -> assertThat(context).doesNotHaveBean(OpaqueTokenIntrospector.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	void autoConfigurationShouldConfigureResourceServerUsingJwkSetUriAndIssuerUri() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String path = "test";
		String issuer = this.server.url(path).toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponse(cleanIssuerPath);
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com",
						"spring.security.oauth2.resourceserver.jwt.issuer-uri=http://" + this.server.getHostName() + ":"
								+ this.server.getPort() + "/" + path)
				.run((context) -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
					assertThat(jwtDecoder).extracting("jwtValidator.tokenValidators")
							.asInstanceOf(InstanceOfAssertFactories.collection(OAuth2TokenValidator.class))
							.hasAtLeastOneElementOfType(JwtIssuerValidator.class);
				});
	}

	@SuppressWarnings("unchecked")
	@Test
	void autoConfigurationShouldNotConfigureIssuerUriAndAudienceJwtValidatorIfPropertyNotConfigured() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String path = "test";
		String issuer = this.server.url(path).toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponse(cleanIssuerPath);
		this.contextRunner
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com")
				.run((context) -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
					assertThat(jwtDecoder).extracting("jwtValidator.tokenValidators")
							.asInstanceOf(InstanceOfAssertFactories.collection(OAuth2TokenValidator.class))
							.hasExactlyElementsOfTypes(JwtTimestampValidator.class)
							.doesNotHaveAnyElementsOfTypes(JwtClaimValidator.class)
							.doesNotHaveAnyElementsOfTypes(JwtIssuerValidator.class);
				});
	}

	@Test
	void autoConfigurationShouldConfigureAudienceAndIssuerJwtValidatorIfPropertyProvided() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String path = "test";
		String issuer = this.server.url(path).toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponse(cleanIssuerPath);
		String issuerUri = "http://" + this.server.getHostName() + ":" + this.server.getPort() + "/" + path;
		this.contextRunner.withPropertyValues(
				"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com",
				"spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuerUri,
				"spring.security.oauth2.resourceserver.jwt.audiences=https://test-audience.com,https://test-audience1.com")
				.run((context) -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
					validate(issuerUri, jwtDecoder);
				});
	}

	@SuppressWarnings("unchecked")
	@Test
	void autoConfigurationShouldConfigureAudienceValidatorIfPropertyProvidedAndIssuerUri() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String path = "test";
		String issuer = this.server.url(path).toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponse(cleanIssuerPath);
		String issuerUri = "http://" + this.server.getHostName() + ":" + this.server.getPort() + "/" + path;
		this.contextRunner.withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuerUri,
				"spring.security.oauth2.resourceserver.jwt.audiences=https://test-audience.com,https://test-audience1.com")
				.run((context) -> {
					SupplierJwtDecoder supplierJwtDecoderBean = context.getBean(SupplierJwtDecoder.class);
					Supplier<JwtDecoder> jwtDecoderSupplier = (Supplier<JwtDecoder>) ReflectionTestUtils
							.getField(supplierJwtDecoderBean, "jwtDecoderSupplier");
					JwtDecoder jwtDecoder = jwtDecoderSupplier.get();
					validate(issuerUri, jwtDecoder);
				});
	}

	@SuppressWarnings("unchecked")
	private void validate(String issuerUri, JwtDecoder jwtDecoder) throws MalformedURLException {
		DelegatingOAuth2TokenValidator<Jwt> jwtValidator = (DelegatingOAuth2TokenValidator<Jwt>) ReflectionTestUtils
				.getField(jwtDecoder, "jwtValidator");
		Jwt.Builder builder = jwt().claim("aud", Collections.singletonList("https://test-audience.com"));
		if (issuerUri != null) {
			builder.claim("iss", new URL(issuerUri));
		}
		Jwt jwt = builder.build();
		assertThat(jwtValidator.validate(jwt).hasErrors()).isFalse();
		Collection<OAuth2TokenValidator<Jwt>> delegates = (Collection<OAuth2TokenValidator<Jwt>>) ReflectionTestUtils
				.getField(jwtValidator, "tokenValidators");
		validateDelegates(issuerUri, delegates);
	}

	@SuppressWarnings("unchecked")
	private void validateDelegates(String issuerUri, Collection<OAuth2TokenValidator<Jwt>> delegates) {
		assertThat(delegates).hasAtLeastOneElementOfType(JwtClaimValidator.class);
		OAuth2TokenValidator<Jwt> delegatingValidator = delegates.stream()
				.filter((v) -> v instanceof DelegatingOAuth2TokenValidator).findFirst().get();
		if (issuerUri != null) {
			assertThat(delegatingValidator).extracting("tokenValidators")
					.asInstanceOf(InstanceOfAssertFactories.collection(OAuth2TokenValidator.class))
					.hasAtLeastOneElementOfType(JwtIssuerValidator.class);
		}
	}

	@Test
	void autoConfigurationShouldConfigureAudienceValidatorIfPropertyProvidedAndPublicKey() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String path = "test";
		String issuer = this.server.url(path).toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponse(cleanIssuerPath);
		this.contextRunner.withPropertyValues(
				"spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:public-key-location",
				"spring.security.oauth2.resourceserver.jwt.audiences=https://test-audience.com,http://test-audience1.com")
				.run((context) -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
					validate(null, jwtDecoder);
				});
	}

	@SuppressWarnings("unchecked")
	@Test
	void audienceValidatorWhenAudienceInvalid() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		String path = "test";
		String issuer = this.server.url(path).toString();
		String cleanIssuerPath = cleanIssuerPath(issuer);
		setupMockResponse(cleanIssuerPath);
		String issuerUri = "http://" + this.server.getHostName() + ":" + this.server.getPort() + "/" + path;
		this.contextRunner.withPropertyValues(
				"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com",
				"spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuerUri,
				"spring.security.oauth2.resourceserver.jwt.audiences=https://test-audience.com,https://test-audience1.com")
				.run((context) -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
					DelegatingOAuth2TokenValidator<Jwt> jwtValidator = (DelegatingOAuth2TokenValidator<Jwt>) ReflectionTestUtils
							.getField(jwtDecoder, "jwtValidator");
					Jwt jwt = jwt().claim("iss", new URL(issuerUri))
							.claim("aud", Collections.singletonList("https://other-audience.com")).build();
					assertThat(jwtValidator.validate(jwt).hasErrors()).isTrue();
				});
	}

	@Test
	void jwtSecurityConfigurerBacksOffWhenSecurityFilterChainBeanIsPresent() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class))
				.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwk-set-uri.com")
				.withUserConfiguration(JwtDecoderConfig.class, TestSecurityFilterChainConfig.class)
				.run((context) -> assertThat(context).hasSingleBean(SecurityFilterChain.class));
	}

	@Test
	void opaqueTokenSecurityConfigurerBacksOffWhenSecurityFilterChainBeanIsPresent() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class))
				.withUserConfiguration(TestSecurityFilterChainConfig.class)
				.withPropertyValues(
						"spring.security.oauth2.resourceserver.opaquetoken.introspection-uri=https://check-token.com",
						"spring.security.oauth2.resourceserver.opaquetoken.client-id=my-client-id",
						"spring.security.oauth2.resourceserver.opaquetoken.client-secret=my-client-secret")
				.run((context) -> assertThat(context).hasSingleBean(SecurityFilterChain.class));
	}

	private Filter getBearerTokenFilter(AssertableWebApplicationContext context) {
		FilterChainProxy filterChain = (FilterChainProxy) context.getBean(BeanIds.SPRING_SECURITY_FILTER_CHAIN);
		List<SecurityFilterChain> filterChains = filterChain.getFilterChains();
		List<Filter> filters = filterChains.get(0).getFilters();
		return filters.stream().filter((f) -> f instanceof BearerTokenAuthenticationFilter).findFirst().orElse(null);
	}

	private String cleanIssuerPath(String issuer) {
		if (issuer.endsWith("/")) {
			return issuer.substring(0, issuer.length() - 1);
		}
		return issuer;
	}

	private void setupMockResponse(String issuer) throws JsonProcessingException {
		MockResponse mockResponse = new MockResponse().setResponseCode(HttpStatus.OK.value())
				.setBody(new ObjectMapper().writeValueAsString(getResponse(issuer)))
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		this.server.enqueue(mockResponse);
		this.server.enqueue(
				new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(JWK_SET));
	}

	private void setupMockResponsesWithErrors(String issuer, int errorResponseCount) throws JsonProcessingException {
		for (int i = 0; i < errorResponseCount; i++) {
			MockResponse emptyResponse = new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
			this.server.enqueue(emptyResponse);
		}
		setupMockResponse(issuer);
	}

	private Map<String, Object> getResponse(String issuer) {
		Map<String, Object> response = new HashMap<>();
		response.put("authorization_endpoint", "https://example.com/o/oauth2/v2/auth");
		response.put("claims_supported", Collections.emptyList());
		response.put("code_challenge_methods_supported", Collections.emptyList());
		response.put("id_token_signing_alg_values_supported", Collections.emptyList());
		response.put("issuer", issuer);
		response.put("jwks_uri", issuer + "/.well-known/jwks.json");
		response.put("response_types_supported", Collections.emptyList());
		response.put("revocation_endpoint", "https://example.com/o/oauth2/revoke");
		response.put("scopes_supported", Collections.singletonList("openid"));
		response.put("subject_types_supported", Collections.singletonList("public"));
		response.put("grant_types_supported", Collections.singletonList("authorization_code"));
		response.put("token_endpoint", "https://example.com/oauth2/v4/token");
		response.put("token_endpoint_auth_methods_supported", Collections.singletonList("client_secret_basic"));
		response.put("userinfo_endpoint", "https://example.com/oauth2/v3/userinfo");
		return response;
	}

	static Jwt.Builder jwt() {
		// @formatter:off
		return Jwt.withTokenValue("token")
				.header("alg", "none")
				.expiresAt(Instant.MAX)
				.issuedAt(Instant.MIN)
				.issuer("https://issuer.example.org")
				.jti("jti")
				.notBefore(Instant.MIN)
				.subject("mock-test-subject");
		// @formatter:on
	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebSecurity
	static class TestConfig {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebSecurity
	static class JwtDecoderConfig {

		@Bean
		JwtDecoder decoder() {
			return mock(JwtDecoder.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebSecurity
	static class OpaqueTokenIntrospectorConfig {

		@Bean
		OpaqueTokenIntrospector decoder() {
			return mock(OpaqueTokenIntrospector.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebSecurity
	static class TestSecurityFilterChainConfig {

		@Bean
		SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
			http.securityMatcher("/**");
			http.authorizeHttpRequests().anyRequest().authenticated();
			return http.build();
		}

	}

}
