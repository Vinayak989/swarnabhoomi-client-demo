package in.swarnabhoomi.farmlog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Conditional(S3BucketConfiguredCondition.class)
public class S3StorageConfiguration {
    @Bean
    S3Client s3Client(@Value("${app.storage.s3.region}") String region,
            @Value("${AWS_ACCESS_KEY_ID:}") String accessKeyId,
            @Value("${AWS_SECRET_ACCESS_KEY:}") String secretAccessKey,
            @Value("${AWS_SESSION_TOKEN:}") String sessionToken) {
        if (accessKeyId.isBlank() || secretAccessKey.isBlank()) {
            throw new IllegalStateException(
                    "S3_BUCKET is configured, but AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY are required.");
        }

        var credentials = sessionToken.isBlank()
                ? AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                : AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
