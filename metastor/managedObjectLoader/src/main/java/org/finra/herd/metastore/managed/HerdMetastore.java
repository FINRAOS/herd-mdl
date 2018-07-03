/*
 * Copyright 2018 herd-mdl contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
**/
package org.finra.herd.metastore.managed;

import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.conf.HerdMetastoreConfig;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
@SpringBootApplication
public class HerdMetastore {


	public static void main( String args[] ) {
		SpringApplication app = new SpringApplication( HerdMetastore.class, HerdMetastoreConfig.class );
		app.setBannerMode( Banner.Mode.OFF );

		ConfigurableApplicationContext ctx = app.run( args );

		ObjectProcessor objectProcessor = ctx.getBean( ObjectProcessor.class );
		objectProcessor.runJobs();
	}


}
