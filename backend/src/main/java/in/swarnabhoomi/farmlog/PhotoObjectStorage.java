package in.swarnabhoomi.farmlog;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Component
public class PhotoObjectStorage {
    private final ObjectProvider<S3Client> clientProvider;
    private final String bucket;

    public PhotoObjectStorage(ObjectProvider<S3Client> clientProvider,
            @Value("${app.storage.s3.bucket:}") String bucket) {
        this.clientProvider = clientProvider;
        this.bucket = bucket;
    }

    public boolean isS3Enabled() {
        return !bucket.isBlank() && clientProvider.getIfAvailable() != null;
    }

    public void store(FarmPhoto photo) {
        if (!isS3Enabled()) {
            photo.setS3Key(null);
            return;
        }
        if (photo.getData() == null || photo.getData().length == 0) {
            throw new IllegalArgumentException("Photo data is required for S3 upload.");
        }
        String key = "photos/" + photo.getId();
        clientProvider.getObject().putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(photo.getContentType())
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build(),
                RequestBody.fromBytes(photo.getData()));
        photo.setS3Key(key);
        photo.setData(null);
    }

    public byte[] load(FarmPhoto photo) {
        if (photo.getS3Key() == null || photo.getS3Key().isBlank()) {
            if (photo.getData() == null) {
                throw new IllegalStateException("Photo has neither an S3 object nor database content.");
            }
            return photo.getData();
        }
        ResponseBytes<GetObjectResponse> response = clientProvider.getObject().getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(photo.getS3Key()).build());
        return response.asByteArray();
    }

    public void migrateToS3(FarmPhoto photo) {
        if (photo.getS3Key() != null && !photo.getS3Key().isBlank()) return;
        if (!isS3Enabled()) {
            throw new IllegalStateException("S3 bucket and AWS credentials are required to migrate photos.");
        }
        store(photo);
    }

    public String newPhotoId() {
        return UUID.randomUUID().toString();
    }

    public void delete(FarmPhoto photo) {
        if (photo.getS3Key() != null && !photo.getS3Key().isBlank() && isS3Enabled()) {
            clientProvider.getObject().deleteObject(request -> request.bucket(bucket).key(photo.getS3Key()));
        }
    }
}
