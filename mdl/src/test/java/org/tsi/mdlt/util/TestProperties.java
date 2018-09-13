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
package org.tsi.mdlt.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.tsi.mdlt.aws.CloudFormationClient;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;

//TODO this deployHost.property and test.properties need to managed/merged together
/**
 * Properties from both mdl/.props and deployHost.props
 */
public class TestProperties {

    private static Properties propertyValues;

    static {
        try {
            propertyValues = new Properties();

            //replace default test parameters with DeployPropertiesFile
            Properties systemPropertyValues = System.getProperties();
            for (Object propertyKey : systemPropertyValues.keySet()) {
                if (propertyKey.toString().equals("DeployPropertiesFile")) {
                    String propertyFileName = systemPropertyValues.getProperty(propertyKey.toString());
                    propertyValues.load(new FileInputStream(propertyFileName));
                }
            }
            propertyValues.load(new FileInputStream("./mdlt/conf/test.props"));
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to load resource", e);
        }
    }

    public static Properties getProperties() {
        return propertyValues;
    }

    public static Map<String, String> getPropertyMap() {
        Map<String, String> map = new HashMap<>();
        for (String key : propertyValues.stringPropertyNames()) {
            map.put(key, propertyValues.getProperty(key));
        }
        return map;
    }

    public static String get(StackInputParameterKeyEnum key) {
        assert propertyValues.getProperty(key.getKey()) != null;
        return propertyValues.getProperty(key.getKey());
    }
}
