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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opencsv.CSVReader;

/**
 * File Reader to read csv file. Assumes that first row is the header with column names.
 */
public class CsvFileReader {

    /**
     * Loads content of the csv file into a List of rows in the CSV. Each row is represented by a Map of columnName->columnValue. This assumes that first row is
     * the header with column names.
     *
     * @param filePath file path to be read from
     * @return List of map for columns key/value pairs
     */
    public static List<Map<String, String>> readCSVFile(String filePath) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
        List<Map<String, String>> contents = new ArrayList<>();
        int lineNumber = 1;
        String[] row;
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream))) {
            String[] header = csvReader.readNext();
            while ((row = csvReader.readNext()) != null) {
                lineNumber++;
                Map<String, String> rowMap = new HashMap<>();
                for (int i = 0; i < row.length; i++) {
                    rowMap.put(header[i].trim(), row[i].trim());
                }
                contents.add(rowMap);
            }
        }
        catch (IOException | RuntimeException e) {
            throw new RuntimeException("Error reading CSV file:" + filePath + " at line " + lineNumber,
                    e);
        }
        return contents;
    }
}
