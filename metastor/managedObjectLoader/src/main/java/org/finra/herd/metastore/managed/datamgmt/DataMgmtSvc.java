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
package org.finra.herd.metastore.managed.datamgmt;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.util.MetastoreUtil;
import org.finra.herd.sdk.api.BusinessObjectDataApi;
import org.finra.herd.sdk.api.BusinessObjectDataNotificationRegistrationApi;
import org.finra.herd.sdk.api.BusinessObjectFormatApi;
import org.finra.herd.sdk.api.EmrApi;
import org.finra.herd.sdk.invoker.ApiClient;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Herd Client
 */
@Component
@Slf4j
public class DataMgmtSvc {

    @Value("${AGS}")
    private String ags;

    @Value("${CLUSTER_DEF_NAME}")
    String clusterDef;

    @Value("${CLUSTER_DEF_NAME_STATS}")
    String clusterDefNameStats;

    @Autowired
    ApiClient dmApiClient;


    @Autowired
    BusinessObjectDataApi businessObjectDataApi;

    static {
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
                (hostname, sslSession) -> true);
    }

    public String getTableSchema(org.finra.herd.metastore.managed.JobDefinition jd, boolean replaceColumn) throws ApiException {

        BusinessObjectFormatApi businessObjectFormatApi = new BusinessObjectFormatApi(dmApiClient);

        BusinessObjectFormatDdlRequest request = new BusinessObjectFormatDdlRequest();
        request.setBusinessObjectDefinitionName(jd.getActualObjectName());
        request.setBusinessObjectFormatFileType(jd.getObjectDefinition().getFileType());
        request.setBusinessObjectFormatUsage(jd.getObjectDefinition().getUsageCode());

        request.setNamespace(jd.getObjectDefinition().getNameSpace());
        request.setIncludeDropTableStatement(false);
        request.setIncludeIfNotExistsOption(!replaceColumn);
        request.setOutputFormat(BusinessObjectFormatDdlRequest.OutputFormatEnum.HIVE_13_DDL);
        request.setReplaceColumns(replaceColumn);

        request.setTableName(jd.getTableName());

        BusinessObjectFormatDdl ddl = businessObjectFormatApi.businessObjectFormatGenerateBusinessObjectFormatDdl(request);

        return ddl.getDdl();
    }

    public BusinessObjectFormat getDMFormat(org.finra.herd.metastore.managed.JobDefinition jd) throws ApiException {

        BusinessObjectFormatApi businessObjectFormatApi = new BusinessObjectFormatApi(dmApiClient);

        BusinessObjectFormat format = businessObjectFormatApi.businessObjectFormatGetBusinessObjectFormat(jd.getObjectDefinition().getNameSpace(),
                jd.getActualObjectName(), jd.getObjectDefinition().getUsageCode(), jd.getObjectDefinition().getFileType(), null);

        return format;
    }

    public BusinessObjectDataDdl getBusinessObjectDataDdl(org.finra.herd.metastore.managed.JobDefinition jd, List<String> partitions) throws ApiException {
        BusinessObjectDataDdlRequest request = new BusinessObjectDataDdlRequest();

        request.setIncludeDropTableStatement(false);
        request.setOutputFormat(BusinessObjectDataDdlRequest.OutputFormatEnum.HIVE_13_DDL);
        request.setBusinessObjectFormatUsage(jd.getObjectDefinition().getUsageCode());
        request.setBusinessObjectFormatFileType(jd.getObjectDefinition().getFileType());
        request.setBusinessObjectDefinitionName(jd.getObjectDefinition().getObjectName());
        request.setAllowMissingData(true);
        request.setIncludeDropPartitions(true);
        request.setIncludeIfNotExistsOption(true);
        request.setTableName(jd.getTableName());

        List<PartitionValueFilter> partitionValueFilters = Lists.newArrayList();

        log.info("PartitionKey: {} \t Partitions: {}", jd.getPartitionKey(), partitions);
        if (MetastoreUtil.isPartitionedSingleton(jd.getWfType(), jd.getPartitionKey())) {
            addPartitionedSingletonFilter(jd, partitionValueFilters);
        } else {

            if (MetastoreUtil.isNonPartitionedSingleton(jd.getWfType(), jd.getPartitionKey())) {
                addPartitionFilter(jd.getPartitionKey(), Lists.newArrayList("none"), partitionValueFilters);
            } else {
                if (jd.isSubPartitionLevelProcessing()) {
                    addSubPartitionFilter(jd, partitionValueFilters);
                } else {
                    addPartitionFilter(jd.getPartitionKey(), partitions, partitionValueFilters);
                }
            }
        }

        request.setPartitionValueFilter(null);
        request.setPartitionValueFilters(partitionValueFilters);
        request.setNamespace(jd.getObjectDefinition().getNameSpace());

        log.info("Get BO DDL Request: \n{}", request.toString());
        return businessObjectDataApi.businessObjectDataGenerateBusinessObjectDataDdl(request);
    }

    private void addPartitionFilter(String partitionKey, List<String> partitions, List<PartitionValueFilter> partitionValueFilters) {
        PartitionValueFilter filter = new PartitionValueFilter();
        filter.setPartitionKey(partitionKey);
        filter.setPartitionValues(partitions);
        partitionValueFilters.add(filter);
    }

    private void addPartitionedSingletonFilter(JobDefinition jd, List<PartitionValueFilter> partitionValueFilters) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, 1);
        String date = new SimpleDateFormat("YYYY-MM-dd").format(c.getTime());
        LatestBeforePartitionValue value = new LatestBeforePartitionValue();
        value.setPartitionValue(date);

        PartitionValueFilter filter = new PartitionValueFilter();
        filter.setPartitionKey(jd.getPartitionKey());
        filter.setLatestBeforePartitionValue(value);
        filter.setPartitionValues(null);
        filter.setLatestAfterPartitionValue(null);
        partitionValueFilters.add(filter);
    }

    /**
     * To partition filter with sub partitions
     *
     * @param jd
     * @param partitionValueFilters
     * @throws ApiException
     */
    private void addSubPartitionFilter(JobDefinition jd, List<PartitionValueFilter> partitionValueFilters) throws ApiException {
        List<SchemaColumn> partitionKeys = getDMFormat(jd).getSchema().getPartitions();
        log.info("Partition Keys {} for {}", partitionKeys, jd.getTableName());
        Map<String, String> partitionKeyValues = Maps.newLinkedHashMap();

        IntStream.range(0, jd.getPartitionValues().size())
                .forEach(i -> {
                    String partitionKey = partitionKeys.get(i).getName();
                    String partitionValue = jd.getPartitionValues().get(i);
                    log.info("Partition Key: {}\t Value: {}", partitionKey, partitionValue);
                    partitionKeyValues.put(partitionKey, partitionValue);

                    addPartitionFilter(partitionKey, Lists.newArrayList(partitionValue), partitionValueFilters);
                });

        jd.setPartitionsKeyValue(partitionKeyValues);
    }

    public BusinessObjectFormatKeys getBOAllFormatVersions(org.finra.herd.metastore.managed.JobDefinition od, boolean latestBusinessObjectFormatVersion) throws ApiException {

        return new BusinessObjectFormatApi(dmApiClient)
                .businessObjectFormatGetBusinessObjectFormats(
                        od.getObjectDefinition().getNameSpace()
                        , od.getObjectDefinition().getObjectName()
                        , latestBusinessObjectFormatVersion);
    }

    public BusinessObjectDataNotificationRegistrationKeys getBORegisteredNotification(org.finra.herd.metastore.managed.JobDefinition od) throws ApiException {
        return new BusinessObjectDataNotificationRegistrationApi(dmApiClient)
                .businessObjectDataNotificationRegistrationGetBusinessObjectDataNotificationRegistrationsByNotificationFilter(
                        od.getObjectDefinition().getNameSpace()
                        , od.getObjectDefinition().getObjectName()
                        , od.getObjectDefinition().getUsageCode()
                        , od.getObjectDefinition().getFileType()

                );
    }

    public BusinessObjectDataNotificationRegistration getBORegisteredNotificationDetails(String notificationName) throws ApiException {
        return new BusinessObjectDataNotificationRegistrationApi(dmApiClient)
                .businessObjectDataNotificationRegistrationGetBusinessObjectDataNotificationRegistration(ags, notificationName);
    }


    public void createCluster( boolean startStatsCluster, String proposedName ) throws ApiException {
        EmrApi emrApi = new EmrApi(dmApiClient);
        EmrClusterCreateRequest request = new EmrClusterCreateRequest();
        request.setNamespace( ags );
        request.setDryRun(false);

        request.setEmrClusterDefinitionName(clusterDef);
        if ( startStatsCluster ) {
            request.setEmrClusterDefinitionName(clusterDefNameStats);
        }

        request.setEmrClusterName( proposedName);
        EmrCluster cluster = emrApi.eMRCreateEmrCluster(request);
        log.info(cluster.toString());
    }

    public BusinessObjectDataSearchResult searchBOData(JobDefinition jd, int pageNum, int pageSize, Boolean filterOnValidLatestVersions) throws ApiException {
        // Create Search Key
        BusinessObjectDataSearchKey boDataSearchKeyItem = new BusinessObjectDataSearchKey();
        boDataSearchKeyItem.setNamespace(jd.getObjectDefinition().getNameSpace());
        boDataSearchKeyItem.setBusinessObjectDefinitionName(jd.getObjectDefinition().getObjectName());
        boDataSearchKeyItem.setBusinessObjectFormatUsage(jd.getObjectDefinition().getUsageCode());
        boDataSearchKeyItem.setBusinessObjectFormatFileType(jd.getObjectDefinition().getFileType());
        boDataSearchKeyItem.setFilterOnLatestValidVersion(filterOnValidLatestVersions);


        // Search BO Data
        return businessObjectDataApi.businessObjectDataSearchBusinessObjectData(
                new BusinessObjectDataSearchRequest()
                        .addBusinessObjectDataSearchFiltersItem(new BusinessObjectDataSearchFilter()
                                .addBusinessObjectDataSearchKeysItem(boDataSearchKeyItem)
                        )
                , pageNum
                , pageSize);
    }
}
