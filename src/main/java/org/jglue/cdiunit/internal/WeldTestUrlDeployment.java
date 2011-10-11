/*
 *    Copyright 2011 Bryn Cooke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jglue.cdiunit.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.enterprise.inject.spi.Extension;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.interceptor.Interceptor;

import org.jboss.weld.bootstrap.api.Bootstrap;
import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.bootstrap.spi.BeansXml;
import org.jboss.weld.bootstrap.spi.Metadata;
import org.jboss.weld.bootstrap.spi.Scanning;
import org.jboss.weld.environment.se.WeldSEBeanRegistrant;
import org.jboss.weld.environment.se.discovery.AbstractWeldSEDeployment;
import org.jboss.weld.environment.se.discovery.ImmutableBeanDeploymentArchive;
import org.jboss.weld.metadata.BeansXmlImpl;
import org.jboss.weld.metadata.MetadataImpl;
import org.jboss.weld.resources.spi.ResourceLoader;
import org.jglue.cdiunit.MockExtension;
import org.jglue.cdiunit.SupportClasses;
import org.jglue.cdiunit.TestAlternative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeldTestUrlDeployment extends AbstractWeldSEDeployment {
	private final BeanDeploymentArchive _beanDeploymentArchive;
	private Collection<Metadata<Extension>> _extensions = new ArrayList<Metadata<Extension>>();
	private static Logger log = LoggerFactory.getLogger(WeldTestUrlDeployment.class);

	public WeldTestUrlDeployment(ResourceLoader resourceLoader, Bootstrap bootstrap, Class<?> testClass) {
		super(bootstrap);
		// BeanDeploymentArchive archive = new URLScanner(resourceLoader,
		// bootstrap, RESOURCES).scan();
		BeansXml beansXml = new BeansXmlImpl(new ArrayList<Metadata<String>>(), new ArrayList<Metadata<String>>(),
				new ArrayList<Metadata<String>>(), new ArrayList<Metadata<String>>(), Scanning.EMPTY_SCANNING);
		Set<String> discoveredClasses = new HashSet<String>();
		discoveredClasses.add(testClass.getName());
		Set<Class<?>> classesToProcess = new LinkedHashSet<Class<?>>();
		Set<Class<?>> classesProcessed = new HashSet<Class<?>>();
		classesToProcess.add(testClass);
		try {
			Class.forName("javax.servlet.http.HttpServletRequest");
			classesToProcess.add(InRequestInterceptor.class);
			classesToProcess.add(InSessionInterceptor.class);
			classesToProcess.add(InConversationInterceptor.class);
			_extensions.add(new MetadataImpl<Extension>(new TestScopeExtension(testClass), TestScopeExtension.class.getName()));

		}
		catch(ClassNotFoundException e) {
		}
		while (!classesToProcess.isEmpty()) {
			Class<?> c = classesToProcess.iterator().next();
			if (!classesProcessed.contains(c) && !c.isInterface() && !c.isPrimitive()) {
				discoveredClasses.add(c.getName());
				if (Extension.class.isAssignableFrom(c)) {
					try {
						_extensions.add(new MetadataImpl<Extension>((Extension) c.newInstance(), c.getName()));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				if (c.isAnnotationPresent(Interceptor.class)) {
					beansXml.getEnabledInterceptors().add(new MetadataImpl<String>(c.getName(), c.getName()));
				}
				SupportClasses supportClasses = c.getAnnotation(SupportClasses.class);
				if (supportClasses != null) {
					for (Class<?> supportClass : supportClasses.value()) {
						classesToProcess.add(supportClass);
					}
				}
				Class<?> superClass = c.getSuperclass();
				if (superClass != null && superClass != Object.class) {
					classesToProcess.add(superClass);
				}

				for (Field field : c.getDeclaredFields()) {
					if (field.isAnnotationPresent(Inject.class)) {
						Class<?> type = field.getType();
						classesToProcess.add(type);
					}
					if (field.getType().equals(Provider.class)) {
						ParameterizedType type = (ParameterizedType)field.getGenericType();
						classesToProcess.add((Class<?>)type.getActualTypeArguments()[0]);
					}
				}
				for (Method method : c.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Inject.class)) {
						for (Class<?> param : method.getParameterTypes()) {
							classesToProcess.add(param);
						}
					}
				}
			}
			classesToProcess.remove(c);
		}
		log.info("Discovered classes:" + discoveredClasses);

		beansXml.getEnabledAlternativeStereotypes().add(
				new MetadataImpl<String>(TestAlternative.class.getName(), TestAlternative.class.getName()));

		_extensions.add(new MetadataImpl<Extension>(new MockExtension(), MockExtension.class.getName()));
		_extensions.add(new MetadataImpl<Extension>(new WeldSEBeanRegistrant(), WeldSEBeanRegistrant.class.getName()));
		
		_beanDeploymentArchive = new ImmutableBeanDeploymentArchive("unitTest", discoveredClasses, beansXml);
		_beanDeploymentArchive.getServices().add(ResourceLoader.class, resourceLoader);

	}

	@Override
	public Iterable<Metadata<Extension>> getExtensions() {
		return _extensions;
	}

	public List<BeanDeploymentArchive> getBeanDeploymentArchives() {
		return Collections.singletonList(_beanDeploymentArchive);
	}

	public BeanDeploymentArchive loadBeanDeploymentArchive(Class<?> beanClass) {
		return _beanDeploymentArchive;
	}
}
