/*
 * Copyright 2010 JBoss, a divison Red Hat, Inc
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

package org.jboss.errai.marshalling.server;

import org.jboss.errai.codegen.framework.meta.MetaField;
import org.jboss.errai.codegen.framework.meta.MetaMethod;
import org.jboss.errai.common.client.protocols.SerializationParts;
import org.jboss.errai.common.client.types.UnsatisfiedForwardLookup;
import org.jboss.errai.marshalling.client.util.NumbersUtils;
import org.jboss.errai.marshalling.rebind.DefinitionsFactory;
import org.jboss.errai.marshalling.rebind.api.model.ConstructorMapping;
import org.jboss.errai.marshalling.rebind.api.model.Mapping;
import org.jboss.errai.marshalling.rebind.api.model.MappingDefinition;
import org.jboss.errai.marshalling.rebind.api.model.MemberMapping;
import org.mvel2.ConversionHandler;
import org.mvel2.DataConversion;
import org.mvel2.MVEL;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mvel2.DataConversion.addConversionHandler;

public class TypeDemarshallHelper {
  public static final String INSTANCE_REFERENCE = "__InstanceReference";

  static {
    addConversionHandler(java.sql.Date.class, new ConversionHandler() {
      public Object convertFrom(Object o) {
        if (o instanceof String) o = Long.parseLong((String) o);

        return new java.sql.Date(((Number) o).longValue());
      }

      public boolean canConvertFrom(Class aClass) {
        return Number.class.isAssignableFrom(aClass);
      }
    });

    addConversionHandler(java.util.Date.class, new ConversionHandler() {
      public Object convertFrom(Object o) {
        if (o instanceof String) o = Long.parseLong((String) o);
        return new java.util.Date(((Number) o).longValue());
      }

      public boolean canConvertFrom(Class aClass) {
        return Number.class.isAssignableFrom(aClass);
      }
    });

    addConversionHandler(StringBuilder.class, new ConversionHandler() {
      public Object convertFrom(Object o) {
        //  if (o instanceof String) o = Long.parseLong((String) o);
        return new StringBuilder((String) o);
      }

      public boolean canConvertFrom(Class aClass) {
        return CharSequence.class.isAssignableFrom(aClass);
      }
    });

    addConversionHandler(StringBuffer.class, new ConversionHandler() {
      public Object convertFrom(Object o) {
        //  if (o instanceof String) o = Long.parseLong((String) o);
        return new StringBuffer((String) o);
      }

      public boolean canConvertFrom(Class aClass) {
        return CharSequence.class.isAssignableFrom(aClass);
      }
    });
  }

  private static final Map<Class, Map<String, Serializable>> MVELDencodingCache = new ConcurrentHashMap<Class, Map<String, Serializable>>();

  public static Class<?> getClassReference(Map oMap) {
    try {
      return Thread.currentThread().getContextClassLoader().loadClass((String) oMap.get(SerializationParts.ENCODED_TYPE));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException("could not instantiate class", e);
    }
  }

  public static Object instantiate(Map oMap, DecodingSession ctx) {
    return instantiate(getClassReference(oMap), oMap, ctx);
  }

  public static Object instantiate(Class clazz, Map oMap, DecodingSession ctx) {
    try {
      String hash = (String) oMap.get(SerializationParts.OBJECT_ID);

      if (ctx.hasObjectHash(hash)) {
        return ctx.getObject(Object.class, hash);
      }
      
      if (clazz.isEnum()) {
        return Enum.valueOf(clazz, (String) oMap.get(SerializationParts.ENUM_STRING_VALUE));
      }
      else if (java.util.Date.class.isAssignableFrom(clazz)) {
        return new java.util.Date(getNumeric(oMap.get("Value")));
      }
      else if (java.sql.Date.class.isAssignableFrom(clazz)) {
        return new java.sql.Date(getNumeric(oMap.get("Value")));
      }

      DefinitionsFactory defs = ctx.getMappingContext().getDefinitionsFactory();

      Object o;
      if (defs.hasDefinition(clazz)) {
        MappingDefinition def = defs.getDefinition(clazz);

        ConstructorMapping cns = def.getConstructorMapping();
        Mapping[] mappings = cns.getMappings();
        Object[] parms = new Object[mappings.length];

        for (int i = 0; i < mappings.length; i++) {
          parms[i] = oMap.get(mappings[i].getKey());
        }

        o = cns.getConstructor().asConstructor().newInstance(parms);
      }
      else {
        o = clazz.newInstance();
      }
      return o;
    }
    catch (InstantiationException e) {
      throw new RuntimeException("error demarshalling", e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException("error demarshalling", e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException("error demarshalling", e);
    }
  }


  public static Object demarshallAll(Object o, DecodingSession ctx) throws Exception {
    try {
      if (o instanceof String) {
        return o;

      }
      else if (o instanceof Collection) {
        ArrayList newList = new ArrayList(((Collection) o).size());
        for (Object o2 : ((Collection) o)) {
          newList.add(demarshallAll(o2, ctx));
        }

        return newList;
      }
      else if (o instanceof Map) {
        Map<?, ?> oMap = (Map) o;

        if (oMap.containsKey(SerializationParts.ENCODED_TYPE)) {
          if (oMap.containsKey(SerializationParts.NUMERIC_VALUE)) {
            return NumbersUtils.getNumber((String) oMap.get(SerializationParts.ENCODED_TYPE),
                    oMap.get(SerializationParts.NUMERIC_VALUE));
          }

          Class<?> cls = getClassReference(oMap);
          Object newInstance;

          if (oMap.size() == 2 && !oMap.containsKey(SerializationParts.INSTANTIATE_ONLY)) {
            return instantiate(cls, oMap, ctx);
          }

          DefinitionsFactory defs = ctx.getMappingContext().getDefinitionsFactory();
          if (defs.hasDefinition(cls)) {
            String hash = (String) oMap.get(SerializationParts.OBJECT_ID);

            ConstructorMapping cMapping;
            if (!ctx.hasObjectHash(hash) && (cMapping = defs.getDefinition(cls).getConstructorMapping()) != null) {
              Constructor c = cMapping.getConstructor().asConstructor();
              Class<?>[] parmTypes = cMapping.getConstructorSignature();

              Object[] parms = new Object[parmTypes.length];
              int i = 0;
              for (Mapping mapping : cMapping.getMappings()) {
                parms[i++] = oMap.get(mapping.getKey());
              }

              newInstance = c.newInstance(parms);
            }
            else {
              newInstance = instantiate(cls, oMap, ctx);
            }

            if (!oMap.containsKey(INSTANCE_REFERENCE)) {
              for (MemberMapping mapping : defs.getDefinition(newInstance.getClass()).getWritableMemberMappings()) {
                if (mapping.getBindingMember() instanceof MetaField) {
                  MetaField f = (MetaField) mapping.getBindingMember();
                  setProperty(newInstance, f.asField(), oMap.get(mapping.getKey()));
                }
                else {
                  Method m = ((MetaMethod) mapping.getBindingMember()).asMethod();
                  m.invoke(newInstance, oMap.get(mapping.getKey()));
                }
              }
            }
          }
          else {
            newInstance = instantiate(cls, oMap, ctx);

            if (!oMap.containsKey(INSTANCE_REFERENCE)) {
              for (Field f : EncodingUtil.getAllEncodingFields(newInstance.getClass())) {
                setProperty(newInstance, f, oMap.get(f.getName()));
              }
            }
          }

          return newInstance;
        }
      }
      return o;
    }
    catch (Exception e) {
      throw new RuntimeException("error demarshalling encoded object:\n" + o, e);
    }
  }


  public static void setProperty(Object i, Field f, Object v) {

    try {
      f.setAccessible(true);
      f.set(i, DataConversion.convert(v, f.getType()));
    }
    catch (Exception e) {
      throw new RuntimeException("could not set field", e);
    }
  }

  public static String ensureSafe(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isJavaIdentifierPart(s.charAt(i))) {
        throw new RuntimeException("illegal character in property");
      }
    }
    return s;
  }

  public static Long getNumeric(Object val) {
    Long longVal;
    if (val instanceof String) {
      longVal = Long.parseLong(String.valueOf(val));
    }
    else if (val instanceof Long) {
      longVal = (Long) val;
    }
    else {
      throw new RuntimeException("expected number: " + val);
    }

    return longVal;
  }
}
