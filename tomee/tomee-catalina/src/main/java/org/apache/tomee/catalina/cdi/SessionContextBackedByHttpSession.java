/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomee.catalina.cdi;

import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.StandardSessionFacade;
import org.apache.openejb.cdi.CdiAppContextsService;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.util.reflection.Reflections;
import org.apache.webbeans.context.creational.BeanInstanceBag;
import org.apache.webbeans.util.WebBeansUtil;

import javax.enterprise.context.spi.Contextual;
import javax.servlet.http.HttpSession;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SessionContextBackedByHttpSession extends CdiAppContextsService.HttpSessionContextSessionAware {
    private static final String WRAPPER = SystemInstance.get().getProperty("tomee.session-context.wrapper", "direct");

    public SessionContextBackedByHttpSession(final HttpSession session) {
        super(session);
        setComponentSessionInstanceMap(); // override default map (set in super())
    }

    @SuppressWarnings("unchecked")
    public void setComponentSessionInstanceMap() {
        HttpSession session = getSession();
        if (session == null) {
            super.setComponentInstanceMap();
            return;
        }

        if (session instanceof StandardSessionFacade) {
            try {
                session = (HttpSession) Reflections.get(session, "session");
            } catch (final Exception e) {
                // no-op
            }
        }

        if (StandardSession.class.equals(session.getClass())) { // local session, use fastest wrapper
            try {
                final ConcurrentHashMap<String, Object> map = (ConcurrentHashMap<String, Object>) Reflections.get(session, "attributes");
                if (WRAPPER.equals("direct")) {
                    componentInstanceMap = new DirectSessionMap(map);
                } else  {
                    componentInstanceMap = new HttpSessionMap(session);
                }
            } catch (final Exception e) {
                componentInstanceMap = new HttpSessionMap(session);
            }
        } else {
            componentInstanceMap = new HttpSessionMap(session);
        }
    }

    public static String key(final Object key) {
        if (key instanceof String) { // avoid nested calls
            return (String) key;
        }

        final String id = WebBeansUtil.getPassivationId((Contextual<?>) key);
        if (id != null) {
            return id;
        }
        return key.toString(); // shouldn't occur
    }

    private static class DirectSessionMap implements ConcurrentMap<Contextual<?>, BeanInstanceBag<?>> {
        private final ConcurrentHashMap<String, Object> delegate;

        public DirectSessionMap(final ConcurrentHashMap<String, Object> map) {
            delegate = map;
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEmpty() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsValue(final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Contextual<?>> keySet() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<BeanInstanceBag<?>> values() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Entry<Contextual<?>, BeanInstanceBag<?>>> entrySet() {
            return Collections.emptySet();
        }

        @Override
        public boolean containsKey(final Object key) {
            return delegate.containsKey(key(key));
        }

        @Override
        public BeanInstanceBag<?> get(final Object key) {
            return (BeanInstanceBag<?>) delegate.get(key(key));
        }

        @Override
        public BeanInstanceBag<?> put(final Contextual<?> key, final BeanInstanceBag<?> value) {
            return (BeanInstanceBag<?>) delegate.put(key(key), value);
        }

        @Override
        public BeanInstanceBag<?> remove(final Object key) {
            return (BeanInstanceBag<?>) delegate.remove(key(key));
        }

        @Override
        public void putAll(final Map<? extends Contextual<?>, ? extends BeanInstanceBag<?>> m) {
            for (final Map.Entry<? extends Contextual<?>, ? extends BeanInstanceBag<?>> e : m.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        }

        @Override
        public void clear() {
            final Iterator<String> it = delegate.keySet().iterator();
            while (it.hasNext()) {
                if (delegate.get(it.next()) instanceof BeanInstanceBag) {
                    it.remove();
                }
            }
        }

        @Override
        public BeanInstanceBag<?> putIfAbsent(final Contextual<?> key, final BeanInstanceBag<?> value) {
            return (BeanInstanceBag<?>) delegate.putIfAbsent(key(key), value);
        }

        @Override
        public boolean remove(final Object key, final Object value) {
            return delegate.remove(key(key), value);
        }

        @Override
        public boolean replace(final Contextual<?> key, final BeanInstanceBag<?> oldValue, final BeanInstanceBag<?> newValue) {
            return delegate.replace(key(key), oldValue, newValue);
        }

        @Override
        public BeanInstanceBag<?> replace(final Contextual<?> key, final BeanInstanceBag<?> value) {
            return (BeanInstanceBag<?>) delegate.replace(key(key), value);
        }
    }

    private static class HttpSessionMap implements ConcurrentMap<Contextual<?>,BeanInstanceBag<?>> { // not sure it can really work
        private final HttpSession session;

        public HttpSessionMap(final HttpSession session) {
            this.session = session;
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEmpty() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsKey(final Object key) {
            return session.getAttribute(key(key)) != null;
        }

        @Override
        public boolean containsValue(final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Contextual<?>> keySet() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<BeanInstanceBag<?>> values() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean replace(final Contextual<?> key, final BeanInstanceBag<?> oldValue, final BeanInstanceBag<?> newValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BeanInstanceBag<?> replace(final Contextual<?> key, final BeanInstanceBag<?> value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            // no-op
        }

        @Override
        public Set<Entry<Contextual<?>, BeanInstanceBag<?>>> entrySet() {
            return Collections.emptySet();
        }

        @Override
        public BeanInstanceBag<?> get(final Object key) {
            return (BeanInstanceBag<?>) session.getAttribute(key(key));
        }

        @Override
        public BeanInstanceBag<?> put(final Contextual<?> key, final BeanInstanceBag<?> value) {
            final BeanInstanceBag<?> bag = get(key);
            session.setAttribute(key(key), value);
            return bag;
        }

        @Override
        public BeanInstanceBag<?> remove(final Object key) {
            final BeanInstanceBag<?> bag = get(key);
            session.removeAttribute(key(key));
            return bag;
        }

        @Override
        public void putAll(final Map<? extends Contextual<?>, ? extends BeanInstanceBag<?>> m) {
            for (final Map.Entry<? extends Contextual<?>, ? extends BeanInstanceBag<?>> e : m.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        }

        @Override
        public BeanInstanceBag<?> putIfAbsent(final Contextual<?> key, final BeanInstanceBag<?> value) {
            final String k = key(key);
            final BeanInstanceBag<?> beanInstanceBag = get(k);
            if (beanInstanceBag == null) {
                return put(key, value);
            }
            return beanInstanceBag;
        }

        @Override
        public boolean remove(final Object key, final Object value) {
            remove(key(key));
            return true;
        }

        @Override
        public String toString() {
            return "HttpSessionMap{session=" + session + '}';
        }
    }

}
