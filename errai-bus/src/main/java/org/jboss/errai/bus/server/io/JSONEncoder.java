/*
 * Copyright 2009 JBoss, a divison Red Hat, Inc
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

package org.jboss.errai.bus.server.io;

import org.jboss.errai.common.client.types.TypeHandler;
import org.mvel2.MVEL;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Encodes an object into a JSON string
 */
public class JSONEncoder {
    private static Set<Class> serializableTypes;

    public static void setSerializableTypes(Set<Class> serializableTypes) {
        JSONEncoder.serializableTypes = serializableTypes;
    }

    public static String encode(Object v) {
        return _encode(v);
    }

    private static String _encode(Object v) {
        if (v == null) {
            return "null";
        } else if (v instanceof String) {
            return "\"" + ((String) v).replaceAll("\"", "\\\\\"") + "\"";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        } else if (v instanceof Collection) {
            return encodeCollection((Collection) v);
        } else if (v instanceof Map) {
            //noinspection unchecked
            return encodeMap((Map) v);
        } else if (v instanceof Object[]) {
            return encodeArray((Object[]) v);
        } else if (serializableTypes.contains(v.getClass()) || tHandlers.containsKey(v.getClass())) {
            return encodeObject(v);
        } else {
            throw new RuntimeException("cannot serialize type: " + v.getClass().getName());
        }
    }

    private static final Map<Class, Serializable[]> MVELEncodingCache = new HashMap<Class, Serializable[]>();

    private static String encodeObject(Object o) {
        if (o == null) return "null";

        Class cls = o.getClass();

        if (tHandlers.containsKey(cls)) {
            return _encode(convert(o));
        }

        StringBuilder build = new StringBuilder("{__EncodedType:'" + cls.getName() + "',");
        Field[] fields = cls.getDeclaredFields();
        int i = 0;

        Serializable[] s = MVELEncodingCache.get(cls);
        if (s == null) {
            synchronized (MVELEncodingCache) {
                // double check after the lock.
                s = MVELEncodingCache.get(cls);
                if (s == null) {
                    s = new Serializable[fields.length];
                    for (Field f : fields) {
                        if ((f.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC)) != 0
                                || f.isSynthetic()) {
                            continue;
                        }
                        s[i++] = MVEL.compileExpression(f.getName());
                    }
                    MVELEncodingCache.put(cls, s);
                }
            }
        }

        i = 0;
        boolean first = true;
        for (Field field : fields) {

            if ((field.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC)) != 0
                    || field.isSynthetic()) {
                continue;
            } else if (!first) {
                build.append(',');
            }

            Object v = MVEL.executeExpression(s[i++], o);
            build.append(field.getName()).append(':').append(_encode(v));
            first = false;
        }

        return build.append('}').toString();
    }

    private static String encodeMap(Map<Object, Object> map) {
        StringBuilder mapBuild = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            String val = _encode(entry.getValue());
            if (!first) {
                mapBuild.append(',');
            }
            mapBuild.append(_encode(entry.getKey()))
                    .append(':').append(val);

            first = false;
        }

        return mapBuild.append('}').toString();
    }

    private static String encodeCollection(Collection col) {
        StringBuilder buildCol = new StringBuilder("[");
        Iterator iter = col.iterator();
        while (iter.hasNext()) {
            buildCol.append(_encode(iter.next()));
            if (iter.hasNext()) buildCol.append(',');
        }
        return buildCol.append(']').toString();
    }

    private static String encodeArray(Object[] array) {
        StringBuilder buildCol = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            buildCol.append(_encode(array[i]));
            if ((i + 1) < array.length) buildCol.append(',');
        }
        return buildCol.append(']').toString();
    }

    private static final Map<Class, TypeHandler> tHandlers = new HashMap<Class, TypeHandler>();

    static {
        tHandlers.put(java.sql.Date.class, new TypeHandler<java.sql.Date, Long>() {
            public Long getConverted(java.sql.Date in) {
                return in.getTime();
            }
        });
        tHandlers.put(java.util.Date.class, new TypeHandler<java.util.Date, Long>() {
            public Long getConverted(java.util.Date in) {
                return in.getTime();
            }
        });
    }

    public static void addEncodingHandler(Class from, TypeHandler handler) {
        tHandlers.put(from, handler);
    }


    private static Object convert(Object in) {
        if (in == null || !tHandlers.containsKey(in.getClass())) return in;
        else {
            //noinspection unchecked
            return tHandlers.get(in.getClass()).getConverted(in);
        }
    }
}
