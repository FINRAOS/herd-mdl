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
package org.tsi.mdlt.enums;

public enum StackOutputKeyEnum {
    MDL_INSTANCE_NAME("MDLInstanceName"),
    APP_STACK_NAME("AppStackName"),
    ROOT_STACK_ID("rootStackId"),
    ES_EC2_DNS("EsEc2DNS"),
    SHEPHERD_URL("ShepherdURL"),
    BDSQL_URL("BdsqlURL"),
    ES_EC2_IP("EsEc2Ip"),
    SHEPHERD_WEBSITE_URL("ShepherdWebSiteBucketUrl"),
    MDL_STAGING_BUCKET_NAME("MDLStagingBucketName"),
    HERD_LB_DNS_NAME("HerdLoadBalancerDNSName"),
    HERD_LB_DNS_URL("HerdLoadBalancerDNSURL"),
    HERD_LB_URL("HerdLoadBalancerURL"),
    HERD_URL("HerdURL"),
    BDSQL_NLB_URL("BdsqlNetworkBalancerURL"),
    BDSQL_NLB_DNS("BdsqlNetworkBalancerDNSName"),
    BDSQL_LB_ARN("BdsqlLoadBalancerArn");

    private String key;

    StackOutputKeyEnum(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
