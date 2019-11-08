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

import lombok.*;

@AllArgsConstructor
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class ObjectDefinition {
    String nameSpace;
    String objectName;
    String usageCode;
    String fileType;
    int wfType;

    public String getDbName()
    {
        String dbName = nameSpace.replaceAll("\\.", "_").replaceAll(" ","_").replaceAll("-", "_");

        return dbName;
    }
}
