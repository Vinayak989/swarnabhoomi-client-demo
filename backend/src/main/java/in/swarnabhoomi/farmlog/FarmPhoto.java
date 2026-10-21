package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "farm_photo")
public class FarmPhoto {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "photo_data")
    private byte[] data;

    @Column(name = "s3_key", length = 512)
    private String s3Key;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
}
