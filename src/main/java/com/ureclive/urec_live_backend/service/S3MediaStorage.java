package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.ChatMedia;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * Keeps chat photos in an S3-compatible bucket: Cloudflare R2 in production (media.storage=s3). The
 * bucket stays private; members get presigned GET URLs that expire.
 */
public class S3MediaStorage implements MediaStorage, AutoCloseable {

    /** R2 ignores the region, but the SDK needs one, and R2's docs say "auto". */
    static final Region REGION = Region.of("auto");
    /** Keys are random, so a file never changes once written. */
    static final String CACHE_CONTROL = "private, max-age=31536000, immutable";

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3MediaStorage(String endpoint, String bucket, String accessKey, String secretKey) {
        this(client(endpoint, accessKey, secretKey), presigner(endpoint, accessKey, secretKey), bucket);
    }

    S3MediaStorage(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl(CACHE_CONTROL)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public String viewUrl(ChatMedia media, Duration ttl) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(get -> get.bucket(bucket).key(media.getStorageKey()))
                        .build())
                .url()
                .toString();
    }

    @Override
    public void close() {
        client.close();
        presigner.close();
    }

    private static StaticCredentialsProvider credentials(String accessKey, String secretKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    /** Path-style URLs (endpoint/bucket/key), which R2 supports for any bucket name. */
    private static S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }

    static S3Client client(String endpoint, String accessKey, String secretKey) {
        return S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30)))
                .endpointOverride(URI.create(endpoint))
                .region(REGION)
                .credentialsProvider(credentials(accessKey, secretKey))
                .serviceConfiguration(pathStyle())
                // R2 doesn't support every checksum newer SDKs add by default; only send them when required
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    static S3Presigner presigner(String endpoint, String accessKey, String secretKey) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(REGION)
                .credentialsProvider(credentials(accessKey, secretKey))
                .serviceConfiguration(pathStyle())
                .build();
    }
}
