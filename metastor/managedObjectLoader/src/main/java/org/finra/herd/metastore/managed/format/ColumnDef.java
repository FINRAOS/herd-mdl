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
package org.finra.herd.metastore.managed.format;

import lombok.*;

@Builder
@Setter
@Getter
@ToString
@AllArgsConstructor
public class ColumnDef {
    private String name;
    private String type;
    private int index;

    public boolean isSameType(ColumnDef columnDef) {
        if (type.equalsIgnoreCase(columnDef.getType())) {
            return true;
        }
        else if (type.toUpperCase().startsWith("DECIMAL") && columnDef.getType().toUpperCase().startsWith("DECIMAL"))
        {
            int[] p1 = parseDecimalType(type);
            int[] p2 = parseDecimalType(columnDef.type);

            return p1[0]==p2[0] && p1[1]==p2[1];
        }
        return false;
    }

    static int[] parseDecimalType(String type)
    {
        int[] pair = new int[]{10, 0};
        type =type.trim();

        if(type.length() > 9) {
            type=type.substring(8, type.length() - 1).trim();
            if (!type.contains(",")) {
                pair[0] = Integer.parseInt(type.trim());
            } else {
                String[] splits = type.split(",");
                pair[0] = Integer.parseInt(splits[0].trim());
                pair[1] = Integer.parseInt(splits[1].trim());
            }
        }
        return pair;
    }

    public boolean isSameColumn(ColumnDef columnDef) {

        if (!name.equalsIgnoreCase(columnDef.name)) return false;
        return isSameType(columnDef);

    }

}
