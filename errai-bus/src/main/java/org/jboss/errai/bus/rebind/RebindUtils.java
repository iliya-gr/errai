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

package org.jboss.errai.bus.rebind;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.jboss.errai.bus.client.api.ErrorCallback;
import org.jboss.errai.bus.client.api.Message;
import org.jboss.errai.bus.client.api.RemoteCallback;
import org.jboss.errai.bus.client.api.interceptor.CallContext;
import org.jboss.errai.codegen.DefParameters;
import org.jboss.errai.codegen.Parameter;
import org.jboss.errai.codegen.Statement;
import org.jboss.errai.codegen.StringStatement;
import org.jboss.errai.codegen.Variable;
import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.meta.MetaClassFactory;
import org.jboss.errai.codegen.meta.MetaMethod;
import org.jboss.errai.codegen.meta.MetaParameter;
import org.jboss.errai.codegen.util.Stmt;
import org.mvel2.util.StringAppender;

import com.google.common.reflect.TypeToken;

/**
 * Utilities to avoid redundant code during code generation (rebind phase).
 * 
 * @author Christian Sadilek <csadilek@redhat.com>
 * @author Mike Brock
 */
public class RebindUtils {
  public static String createCallSignature(MetaMethod m) {
    StringAppender append = new StringAppender(m.getName()).append(':');
    for (MetaParameter parm : m.getParameters()) {
      append.append(parm.getType().getCanonicalName()).append(':');
    }
    return append.toString();
  }

  public static String createCallSignature(Class<?> referenceClass, Method m) {
    TypeToken<?> resolver = TypeToken.of(referenceClass);
    StringAppender append = new StringAppender(m.getName()).append(':');
    for (Type c : m.getGenericParameterTypes()) {
      TypeToken<?> resolvedParamType = resolver.resolveType(c);
      append.append(resolvedParamType.getRawType().getCanonicalName()).append(':');
    }
    return append.toString();
  }

  public static boolean isMethodInInterface(Class iface, Method member) {
    try {
      if (iface.getMethod(member.getName(), member.getParameterTypes()) != null)
        return true;
    }
    catch (NoSuchMethodException e) {
    }
    return false;
  }

  /**
   * Generates a valid return statement for the provided method.
   * 
   * @param method
   * @return return statement for the provided method
   */
  public static Statement generateProxyMethodReturnStatement(MetaMethod method) {
    Statement returnStatement = null;
    if (!method.getReturnType().equals(MetaClassFactory.get(void.class))) {

      // if it's a Number and not a BigDecimal or BigInteger
      if (MetaClassFactory.get(Number.class).isAssignableFrom(method.getReturnType().asBoxed())
              && method.getReturnType().asUnboxed().getFullyQualifiedName().indexOf('.') == -1) {

        if (MetaClassFactory.get(Double.class).isAssignableFrom(method.getReturnType().asBoxed())) {
          returnStatement = Stmt.load(0.0).returnValue();
        }
        else if (MetaClassFactory.get(Float.class).isAssignableFrom(method.getReturnType().asBoxed())) {
          returnStatement = Stmt.load(0f).returnValue();
        }
        else if (MetaClassFactory.get(Long.class).isAssignableFrom(method.getReturnType().asBoxed())) {
          returnStatement = Stmt.load(0l).returnValue();
        }
        else {
          returnStatement = Stmt.load(0).returnValue();
        }
      }
      else if (MetaClassFactory.get(char.class).equals(method.getReturnType())) {
        returnStatement = Stmt.load(0).returnValue();
      }
      else if (MetaClassFactory.get(Boolean.class).isAssignableFrom(method.getReturnType().asBoxed())) {
        returnStatement = Stmt.load(false).returnValue();
      }
      else {
        returnStatement = Stmt.load(null).returnValue();
      }
    }
    return returnStatement;
  }

  /**
   * Generates the {@link CallContext} for method interception.
   * 
   * @param callContextType
   *          the type of {@link CallContext} to use.
   * @param proxyClass
   *          the declaring proxy class
   * @param method
   *          the method that is being proxied.
   * @param proceed
   *          the logic that should be invoked if {@link CallContext#proceed()} is called.
   * @return statement representing an anonymous implementation of the provided {@link CallContext}
   */
  public static Statement generateProxyMethodCallContext(Class<? extends CallContext> callContextType,
      MetaClass proxyClass, MetaMethod method, Statement proceed) {

    Parameter[] parms = DefParameters.from(method).getParameters().toArray(new Parameter[0]);
    List<Statement> parmVars = new ArrayList<Statement>();
    for (Parameter parm : parms) {
      parmVars.add(Stmt.loadVariable(parm.getName()));
    }

    Statement callContext =
        Stmt.newObject(callContextType).extend()
            .publicOverridesMethod("getMethodName")
            .append(Stmt.load(method.getName()).returnValue())
            .finish()
            .publicOverridesMethod("getParameters")
            .append(Stmt.nestedCall(Stmt.newArray(Object.class).initialize(parmVars.toArray())).returnValue())
            .finish()
            .publicOverridesMethod("proceed")
            .append(proceed)
            .finish()
            .publicOverridesMethod("proceed", Parameter.of(RemoteCallback.class, "interceptorCallback", true))
            .append(Stmt.declareVariable(RemoteCallback.class).asFinal().named("providedCallback").initializeWith(
                Stmt.loadStatic(proxyClass, "this").loadField("remoteCallback")))
            .append(
                Stmt.loadVariable("remoteCallback").assignValue(Stmt.newObject(RemoteCallback.class).extend()
                    .publicOverridesMethod("callback", Parameter.of(Object.class, "response"))
                    .append(Stmt.declareVariable(RemoteCallback.class).named("intCallback")
                        .initializeWith(Stmt.loadVariable("interceptorCallback")))
                    .append(new StringStatement("setResult(response)"))
                    .append(Stmt.loadVariable("intCallback").invoke("callback",
                        new StringStatement("getResult()", MetaClassFactory.get(Object.class))))
                    .append(Stmt.loadVariable("providedCallback").invoke("callback",
                        new StringStatement("getResult()", MetaClassFactory.get(Object.class))))
                    .finish()
                    .finish())
            )
            .append(Stmt.loadVariable("this").invoke("proceed"))
            .finish()
            .publicOverridesMethod("proceed", Parameter.of(RemoteCallback.class, "interceptorCallback"),
                Parameter.of(ErrorCallback.class, "interceptorErrorCallback", true))
            .append(
                Stmt.declareVariable(ErrorCallback.class).asFinal().named("providedErrorCallback").initializeWith(
                    Stmt.loadStatic(proxyClass, "this").loadField("errorCallback")))
            .append(
                Stmt.loadVariable("errorCallback").assignValue(
                    Stmt.newObject(ErrorCallback.class).extend()
                        .publicOverridesMethod("error", Parameter.of(Message.class, "message"),
                            Parameter.of(Throwable.class, "throwable"))
                        .append(
                            Stmt.loadVariable("interceptorErrorCallback").invoke("error", Variable.get("message"),
                                Variable.get("throwable")))
                        .append(
                            Stmt.loadVariable("providedErrorCallback").invoke("error", Variable.get("message"),
                                Variable.get("throwable")))
                         .append(Stmt.load(true).returnValue())
                        .finish()
                        .finish())
            )
            .append(Stmt.loadVariable("this").invoke("proceed", Variable.get("interceptorCallback")))
            .finish()
            .finish();

    return callContext;
  }
}