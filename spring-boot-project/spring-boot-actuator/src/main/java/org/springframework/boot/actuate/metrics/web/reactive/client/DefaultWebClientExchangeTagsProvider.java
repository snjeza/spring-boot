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

package org.springframework.boot.actuate.metrics.web.reactive.client;

import java.util.Arrays;

import io.micrometer.core.instrument.Tag;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * Default implementation of {@link WebClientExchangeTagsProvider}.
 *
 * @author Brian Clozel
 * @author Nishant Raut
 * @since 2.1.0
 * @deprecated since 3.0.0 for removal in 3.2.0 in favor of
 * {@link org.springframework.web.reactive.function.client.ClientRequestObservationConvention}
 */
@Deprecated(since = "3.0.0", forRemoval = true)
@SuppressWarnings({ "deprecation", "removal" })
public class DefaultWebClientExchangeTagsProvider implements WebClientExchangeTagsProvider {

	@Override
	public Iterable<Tag> tags(ClientRequest request, ClientResponse response, Throwable throwable) {
		Tag method = WebClientExchangeTags.method(request);
		Tag uri = WebClientExchangeTags.uri(request);
		Tag clientName = WebClientExchangeTags.clientName(request);
		Tag status = WebClientExchangeTags.status(response, throwable);
		Tag outcome = WebClientExchangeTags.outcome(response);
		return Arrays.asList(method, uri, clientName, status, outcome);
	}

}
