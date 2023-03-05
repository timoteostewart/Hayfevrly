package Hayfevrly.Model;

import Hayfevrly.Main;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class AWS {

    public static void uploadStringToBucket(String bucketName, String keyName, String contents) {

        String accessKey = Main.hfSecrets.getProperty("secrets.aws.access_key");
        String secretAccessKey = Main.hfSecrets.getProperty("secrets.aws.secret_access_key");
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretAccessKey);

        AmazonS3 s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.US_EAST_1)
                .build();

        byte[] contentsAsBytes = contents.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream contentsAsByteStream = new ByteArrayInputStream(contentsAsBytes);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setCacheControl("no-cache");
        metadata.setContentEncoding("UTF-8");
        metadata.setContentLanguage("en-US");
        metadata.setContentType("text/html");

        PutObjectRequest por = new PutObjectRequest(bucketName, keyName, contentsAsByteStream, metadata);

        try {
            s3client.putObject(por);
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            Database.logEvent(e.getErrorMessage());
//            System.exit(1);

        }
    }

}
