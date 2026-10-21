package in.swarnabhoomi.farmlog;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class S3BucketConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String bucket = context.getEnvironment().getProperty("app.storage.s3.bucket", "");
        return !bucket.isBlank();
    }
}
