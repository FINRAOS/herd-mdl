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
package org.tsi.mdlt.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAlgorithm;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.s3.transfer.model.UploadResult;
import java.io.InputStream;
import org.finra.herd.model.api.xml.AwsCredential;

public class S3Utils {
    private static final String REGION_NAME = Regions.getCurrentRegion().getName();

    /**
     *
     * @param awsParamsDto object stores aws credential
     * @param is inputstream to upload
     * @param bucketName s3 bucket name
     * @param objectKey s3 obejct key for uploaded stream
     * @param kmsKey kmsKey
     * @return
     * @throws InterruptedException
     */
    public static UploadResult uploadFile(AwsCredential awsParamsDto, InputStream is, String bucketName,
        String objectKey, String kmsKey) throws InterruptedException {

        ObjectMetadata metadata = new ObjectMetadata();
        if (kmsKey != null) {
            metadata.setSSEAlgorithm(SSEAlgorithm.KMS.getAlgorithm());
            metadata.setHeader("x-amz-server-side-encryption-aws-kms-key-id", kmsKey.trim());
        } else {
            metadata.setSSEAlgorithm(SSEAlgorithm.AES256.getAlgorithm());
        }
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, is, metadata);

        TransferManager transferManager = getTransferManager(getAmazonS3(awsParamsDto));
        Upload upload = transferManager.upload(putObjectRequest);
        return upload.waitForUploadResult();
    }

    private static TransferManager getTransferManager(AmazonS3 amazonS3) {
        return TransferManagerBuilder.standard()
            .withS3Client(amazonS3)
            .build();
    }

    private static AmazonS3 getAmazonS3(AwsCredential awsParamsDto) {
        BasicSessionCredentials credentialsProvider = new BasicSessionCredentials(
            awsParamsDto.getAwsAccessKey(), awsParamsDto.getAwsSecretKey(),
            awsParamsDto.getAwsSessionToken());
        AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentialsProvider))
            .withRegion(REGION_NAME)
            .build();
        return amazonS3;
    }
}
