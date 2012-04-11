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

package org.jboss.errai.cdi.rpc.client;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.errai.bus.client.api.RemoteCallback;
import org.jboss.errai.cdi.client.qualifier.A;
import org.jboss.errai.cdi.client.qualifier.B;
import org.jboss.errai.cdi.client.remote.MyInterceptedRemote;
import org.jboss.errai.cdi.client.remote.MyRemote;
import org.jboss.errai.cdi.client.remote.MySessionAttributeSettingRemote;
import org.jboss.errai.ioc.client.api.Caller;

/**
 * @author Mike Brock
 * @author Christian Sadilek <csadilek@redhat.com>
 */
@ApplicationScoped
public class RpcTestBean {

  @Inject
  private Caller<MyRemote> myRemoteCaller;
  
  @Inject
  private Caller<MyInterceptedRemote> myInterceptedRemoteCaller;
  
  @Inject
  private Caller<MySessionAttributeSettingRemote> mySessionAttributeSettingRemoteCaller;
  
  @Inject @A
  private Caller<MyRemote> myRemoteCallerA;
  
  @Inject @B
  private Caller<MyRemote> myRemoteCallerB;

  private static RpcTestBean instance;

  @PostConstruct
  public void init() {
    instance = this;
  }
  
  public void callRemoteCaller(RemoteCallback<String> callback, String val) {
    myRemoteCaller.call(callback).call(val);
  }
  
  public void callInterceptedRemoteCaller(RemoteCallback<String> callback, String val) {
    myInterceptedRemoteCaller.call(callback).interceptedCall(val);
  }
  
  public void callRemoteCallerA(RemoteCallback<String> callback, String val) {
    myRemoteCallerA.call(callback).call(val);
  }                               
  
  public void callRemoteCallerB(RemoteCallback<String> callback, String val) {
    myRemoteCallerB.call(callback).call(val);
  }

  public void callSetSessionAttribute(RemoteCallback<Void> callback, String key, String value) {
     mySessionAttributeSettingRemoteCaller.call(callback).setSessionAttribute(key, value);
  }
  
  public void callGetSessionAttribute(RemoteCallback<String> callback, String key) {
    mySessionAttributeSettingRemoteCaller.call(callback).getSessionAttribute(key);
 }
  
  public static RpcTestBean getInstance() {
    return instance;
  }
}