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
package org.iglue.cdiunit;

import javax.enterprise.inject.Instance;

import org.jboss.weld.bootstrap.api.Bootstrap;
import org.jboss.weld.bootstrap.spi.Deployment;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.jboss.weld.resources.spi.ResourceLoader;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public class CdiUnit extends BlockJUnit4ClassRunner {

	private Class<?> _clazz;

	public CdiUnit(Class<?> clazz) throws InitializationError {
		super(clazz);
		_clazz = clazz;
	}

	@TestAlternative
	protected Object createTest() throws Exception {

		Weld weld = new Weld() {
			protected Deployment createDeployment(
					ResourceLoader resourceLoader, Bootstrap bootstrap) {
				return new WeldTestUrlDeployment(resourceLoader, bootstrap, _clazz);
			};
		};
		WeldContainer container = weld.initialize();
		Instance<?> select = container.instance().select(_clazz);
		Object x = select.get();
		return x;
	}
}