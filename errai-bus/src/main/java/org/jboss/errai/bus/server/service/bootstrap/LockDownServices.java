/*
 * Copyright 2012 JBoss, by Red Hat, Inc
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
package org.jboss.errai.bus.server.service.bootstrap;

import org.jboss.errai.bus.server.ServerMessageBusImpl;

/**
 * @author: Heiko Braun <hbraun@redhat.com>
 * @date: May 10, 2010
 */
public class LockDownServices implements BootstrapExecution {
  public void execute(BootstrapContext context) {
    // freeze unmodifiable services
    ((ServerMessageBusImpl) context.getBus()).finishInit();
  }
}
