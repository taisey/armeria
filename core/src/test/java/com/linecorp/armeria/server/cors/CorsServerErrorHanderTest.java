/*
 * Copyright 2024 LY Corporation
 *
 *         LY Corporation licenses this file to you under the Apache License,
 *         version 2.0 (the "License"); you may not use this file except in compliance
 *         with the License. You may obtain a copy of the License at:
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *         WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *         License for the specific language governing permissions and limitations
 *         under the License.
*/
package com.linecorp.armeria.server.cors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.server.AbstractHttpService;
import com.linecorp.armeria.server.HttpResponseException;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.HttpStatusException;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;

public class CorsServerErrorHanderTest {
    private static final ClientFactory clientFactory = ClientFactory.ofDefault();

    @RegisterExtension
    static final ServerExtension server = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) throws Exception {
            final HttpService myService = new AbstractHttpService() {
                @Override
                protected HttpResponse doGet(ServiceRequestContext ctx, HttpRequest req) {
                    return HttpResponse.of(HttpStatus.OK);
                }

                @Override
                protected HttpResponse doPost(ServiceRequestContext ctx, HttpRequest req) {
                    return HttpResponse.of(HttpStatus.OK);
                }

                @Override
                protected HttpResponse doHead(ServiceRequestContext ctx, HttpRequest req) {
                    return HttpResponse.of(HttpStatus.OK);
                }

                @Override
                protected HttpResponse doOptions(ServiceRequestContext ctx, HttpRequest req) {
                    return HttpResponse.of(HttpStatus.OK);
                }
            };
            addCorsServiceWithException(sb, myService, "/cors_status_exception",
                                        HttpStatusException.of(HttpStatus.INTERNAL_SERVER_ERROR));
            addCorsServiceWithException(sb, myService, "/cors_response_exception",
                                        HttpResponseException.of(
                                        HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR)));
        }
    };

    private static void addCorsServiceWithException(ServerBuilder sb, HttpService myService, String pathPattern,
                                                    Exception exception) {
        sb.service(pathPattern, myService.decorate(
        CorsService.builder("http://example.com")
        .allowRequestMethods(HttpMethod.POST,
                           HttpMethod.GET)
        .allowRequestHeaders("allow_request_header")
        .exposeHeaders("expose_header_1",
                     "expose_header_2")
        .preflightResponseHeader("x-preflight-cors",
                               "Hello CORS")
        .newDecorator())
        .decorate((delegate, ctx, req) -> {
            throw exception;
        }));
    }

    static WebClient client() {
        return WebClient.builder(server.httpUri()).factory(clientFactory).build();
    }

    static AggregatedHttpResponse request(WebClient client, HttpMethod method, String path, String origin,
                                          String requestMethod) {
        return client.execute(RequestHeaders.of(
                method, path, HttpHeaderNames.ACCEPT, "utf-8", HttpHeaderNames.ORIGIN, origin,
                HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD, requestMethod)).aggregate().join();
    }

    static AggregatedHttpResponse preflightRequest(WebClient client, String path, String origin,
                                                   String requestMethod) {
        return request(client, HttpMethod.OPTIONS, path, origin, requestMethod);
    }

    private static void testCorsHeaderWithException(String path) {
        final WebClient client = client();
        final AggregatedHttpResponse response = preflightRequest(client, path,
                                                                 "http://example.com", "GET");
        assertThat(response.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS)).isEqualTo(
                "allow_request_header");
        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(
                "http://example.com");
    }

    @Test
    void testCorsHeaderWhenStatusException() {
        testCorsHeaderWithException("/cors_status_exception");
    }

    @Test
    void testCorsHeaderWhenResponseException() {
        testCorsHeaderWithException("/cors_response_exception");
    }
}