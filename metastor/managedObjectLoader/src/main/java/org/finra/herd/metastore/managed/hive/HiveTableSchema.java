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
package org.finra.herd.metastore.managed.hive;

import com.google.common.collect.Lists;
import lombok.*;
import org.finra.herd.metastore.managed.format.ClusteredDef;
import org.finra.herd.metastore.managed.format.ColumnDef;

import java.util.List;
import java.util.Objects;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HiveTableSchema {

    private List<ColumnDef> columns = Lists.newArrayList();

    private List<ColumnDef> partitionColumns = Lists.newArrayList();

    private ClusteredDef clusteredDef;

    private String escape = "";
    private String delim = "";
    private String nullChar = "";
    private String ddl = "";

    public static boolean isSameChar(String s1, String s2) {
        if (s1.equals(s2)) return true;

        if (s1.length() == s2.length() && s1.codePointAt(0) == s2.codePointAt(0) && s1.length() == 1) {
            return true;
        }

        Integer code1 = extractCode(s1);
        Integer code2 = extractCode(s2);

        if (code1 == null || code2 == null) return false;
        return Objects.equals(code1, code2);
    }

    public static Integer extractCode(String s1) {
        Integer code1 = null;
        if (s1.startsWith("\\u")) {
            code1 = Integer.parseInt(s1.substring(2), 16);
        } else if (s1.startsWith("\\")) {
            try {
                code1 = Integer.parseInt(s1.substring(1));
            } catch (NumberFormatException ex) {

            }
        }
        return code1;
    }
}
