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

import com.amazonaws.services.s3.transfer.model.UploadResult;
import io.restassured.response.Response;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpStatus;
import org.finra.herd.model.api.xml.BusinessObjectData;
import org.finra.herd.model.api.xml.BusinessObjectDataCreateRequest;
import org.finra.herd.model.api.xml.BusinessObjectDataStatusUpdateResponse;
import org.finra.herd.model.api.xml.BusinessObjectDataUploadCredential;
import org.finra.herd.model.api.xml.Storage;
import org.finra.herd.model.api.xml.StorageUnit;
import org.finra.herd.model.api.xml.StorageUnitCreateRequest;
import org.finra.herd.model.jpa.BusinessObjectDataStatusEntity;
import org.tsi.mdlt.aws.S3Utils;
import org.tsi.mdlt.pojos.User;

public class HerdUploader {


    /**
     * Upload default file to Herd with default request settings
     * @param user user to perform upload
     * @param namespace namespace of the file to upload
     * @param bzDefName business definition name of file to upload
     */
    public static UploadResult uploadFile(User user, String namespace, String bzDefName)
        throws InterruptedException {
        InputStream is = FileUtil.getFileInputStream("/data/2017-08-01.data.txt");
        String fileName = "2017-08-01.data.txt";
        BusinessObjectDataCreateRequest dataCreateRequest = getBzDataCreateRequest(namespace,
            bzDefName);
        return uploadFile(user, is , fileName, dataCreateRequest);
    }

    /**
     * Upload file with provided information
     * @param user user to perform upload api calls
     * @param file file to upload
     * @param dataCreateRequest business object data create request object
     */
    public static UploadResult uploadFile(User user, InputStream is, String fileName,
        BusinessObjectDataCreateRequest dataCreateRequest) throws InterruptedException {

        // Pre-register a new version of business object data in UPLOADING state with the registration server.
        Response response = HerdRestUtil
            .uploadBusinessObjectData(user, dataCreateRequest);
        if(response.statusCode() == HttpStatus.SC_CONFLICT){
            BusinessObjectData businessObjectData = getBusinessObjectData(dataCreateRequest);
            response = HerdRestUtil.getBusinessObjectData(user, businessObjectData);
        }

        BusinessObjectData businessObjectData = response.as(BusinessObjectData.class);

        //Get bzData upload credential
        Response credentialResponse = HerdRestUtil
            .getBusinessObjectDataUploadCredential(user, businessObjectData);
        BusinessObjectDataUploadCredential uploadCredential = credentialResponse
            .as(BusinessObjectDataUploadCredential.class);

        //Get s3 key prefix
        String s3KeyPrefix = businessObjectData.getStorageUnits().get(0).getStorageDirectory()
            .getDirectoryPath() + "/";

        //Get s3 bucket info with storage name
        String storageName = dataCreateRequest.getStorageUnits().get(0).getStorageName();
        Storage storage = HerdRestUtil.getStorage(user, storageName).as(Storage.class);
        String s3BucketName = storage.getAttributes().stream()
            .filter(attribute -> attribute.getName().equals("bucket.name"))
            .findFirst().get().getValue();

        //upload file to S3
        UploadResult uploadResult = S3Utils.uploadFile(uploadCredential.getAwsCredential(), is, s3BucketName, s3KeyPrefix + fileName,
            uploadCredential.getAwsKmsKeyId());

        //Update bzObjectData status is valid
        Response statusResponse = HerdRestUtil.updateBusinessObjectDataStatus(user,
            businessObjectData, BusinessObjectDataStatusEntity.VALID);
        BusinessObjectDataStatusUpdateResponse bzObjStatus = statusResponse
            .as(BusinessObjectDataStatusUpdateResponse.class);
        bzObjStatus.getStatus();
        return uploadResult;
    }

    private static BusinessObjectData getBusinessObjectData(BusinessObjectDataCreateRequest createRequest){
        BusinessObjectData businessObjectData = new BusinessObjectData();
        businessObjectData.setNamespace(createRequest.getNamespace());
        businessObjectData.setBusinessObjectDefinitionName(createRequest.getBusinessObjectDefinitionName());
        businessObjectData.setBusinessObjectFormatUsage(createRequest.getBusinessObjectFormatUsage());
        businessObjectData.setBusinessObjectFormatFileType(createRequest.getBusinessObjectFormatFileType());
        businessObjectData.setPartitionValue(createRequest.getPartitionValue());
        return businessObjectData;
    }

    private static BusinessObjectDataCreateRequest getBzDataCreateRequest(String namespace,
        String bzDefName) {
        BusinessObjectDataCreateRequest request = new BusinessObjectDataCreateRequest();
        request.setNamespace(namespace);
        request.setBusinessObjectDefinitionName(bzDefName);
        request.setBusinessObjectFormatUsage("MDL");
        request.setBusinessObjectFormatFileType("TXT");
        request.setBusinessObjectFormatVersion(0);
        request.setPartitionKey("TDATE");
        request.setPartitionValue("2017-01-02");
        request.setCreateNewVersion(true);
        request.setStatus("UPLOADING");
        List<StorageUnitCreateRequest> storageUnits = new ArrayList();
        request.setStorageUnits(storageUnits);
        StorageUnitCreateRequest storageUnit = new StorageUnitCreateRequest();
        storageUnits.add(storageUnit);
        storageUnit.setStorageName("S3_MANAGED");

        return request;
    }

}
