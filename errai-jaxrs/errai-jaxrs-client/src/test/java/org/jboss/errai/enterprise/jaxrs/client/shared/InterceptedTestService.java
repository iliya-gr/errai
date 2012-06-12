/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.enterprise.jaxrs.client.shared;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jboss.errai.bus.client.api.interceptor.InterceptedCall;
import org.jboss.errai.enterprise.jaxrs.client.shared.interceptor.RestCallBypassingInterceptor;
import org.jboss.errai.enterprise.jaxrs.client.shared.interceptor.RestCallResultManipulatingInterceptor;

/**
 * This service is used to test client-side JAX-RS call interceptors.
 * 
 * @author Christian Sadilek <csadilek@redhat.com>
 */
@Path("/test/interceptor")
public interface InterceptedTestService {

  @GET 
  @Path("/1")
  @InterceptedCall(RestCallBypassingInterceptor.class)
  public String interceptedGetBypassingEndpoint();
  
  @GET
  @Path("/2")
  @InterceptedCall(RestCallResultManipulatingInterceptor.class)
  public String interceptedGetManipulatingResult(@QueryParam("result") String result);
}