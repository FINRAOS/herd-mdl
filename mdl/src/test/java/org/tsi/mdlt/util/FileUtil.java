package org.tsi.mdlt.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class FileUtil {

    /**
     * Read file from jar
     * @param filePath file path
     * @return file content as String
     */
    public static String readFileFromJar(String filePath) {
        InputStream in = FileUtil.class.getResourceAsStream(filePath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        return reader.lines().collect(Collectors.joining());
    }
}
