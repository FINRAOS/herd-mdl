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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.tsi.mdlt.enums.StackOutputKeyEnum;

/**
 * Aim to read properties file for stack outputs
 */
public class StackOutputPropertyReader {

    private static final String TEST_PROPERTY_FILE = "./mdlt/conf/test.props";

    private static Map<String, String> propertiesMap;

    private static Map<String, String> loadTestProperties() {
        System.out.println("Load testing properties");
        try {
            propertiesMap = new HashMap<>();
            Properties properties = new Properties();
            properties.load(new FileReader(new File(TEST_PROPERTY_FILE)));
            addPropertiesToMap(properties, propertiesMap);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return propertiesMap;
    }

    public static Map<String, String> getTestProperties() {
        return loadTestProperties();
    }

    /**
     * Get stack output properties with provided key enum
     *
     * @param outputKeyEnum property key from output key enumeration
     * @return property value
     */
    public static String get(StackOutputKeyEnum outputKeyEnum) {
        if (propertiesMap == null) {
            loadTestProperties();
        }
        if (propertiesMap.get(outputKeyEnum.getKey()) == null){
            throw new RuntimeException("output key NOT found: " + outputKeyEnum.getKey());
        }
        return propertiesMap.get(outputKeyEnum.getKey());
    }

    private static Map<String, String> addPropertiesToMap(Properties properties, Map<String, String> map) {
        for (String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return map;
    }
}
