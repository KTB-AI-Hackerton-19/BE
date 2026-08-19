package com.hackathon.backend.service;

import com.hackathon.backend.dto.asset.PresignedUploadResult;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class S3PresignService {

    private static final Logger log = LoggerFactory.getLogger(S3PresignService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "heic");

    /** 선물 사진과 프로필 사진을 경로로 분리해 둔다. 회원탈퇴 시 사용자 폴더를 통째로 지우기도 쉽다. */
    public static final String GIFT_PREFIX = "gift-images";
    public static final String PROFILE_PREFIX = "profile-images";
    private static final Duration PUT_URL_DURATION = Duration.ofSeconds(300);
    private static final Duration GET_URL_DURATION = Duration.ofSeconds(900);

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final String bucket;

    public S3PresignService(S3Presigner s3Presigner, S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    /** 선물 사진용 (기본). */
    public PresignedUploadResult createPutUrl(String username, String fileName, String contentType) {
        return createPutUrl(username, fileName, contentType, GIFT_PREFIX);
    }

    public PresignedUploadResult createPutUrl(String username, String fileName, String contentType, String prefix) {
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }
        String extension = extractExtension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        String imageKey = "%s/%s/%s.%s".formatted(prefix, username, UUID.randomUUID(), extension);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PUT_URL_DURATION)
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        return new PresignedUploadResult(imageKey, uploadUrl, (int) PUT_URL_DURATION.toSeconds());
    }

    public String createGetUrl(String imageKey) {
        if (imageKey == null) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(GET_URL_DURATION)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String extractExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }
        return fileName.substring(idx + 1);
    }

    /**
     * 회원탈퇴 시 그 사용자가 올린 이미지를 전부 지운다(선물 사진 + 프로필 사진).
     * @return 실제로 삭제된 오브젝트 수
     */
    public int deleteAllOf(String username) {
        int deleted = 0;
        for (String prefix : List.of(GIFT_PREFIX, PROFILE_PREFIX)) {
            deleted += deleteByPrefix("%s/%s/".formatted(prefix, username));
        }
        return deleted;
    }

    private int deleteByPrefix(String prefix) {
        try {
            List<ObjectIdentifier> keys = s3Client
                    .listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
                    .contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                    .toList();
            if (keys.isEmpty()) {
                return 0;
            }
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(keys).build())
                    .build());
            return keys.size();
        } catch (S3Exception | software.amazon.awssdk.core.exception.SdkClientException e) {
            log.warn("S3 이미지 삭제 실패 (prefix={}): {} — 탈퇴는 계속 진행합니다.", prefix, e.getMessage());
            return 0;
        }
    }
}
