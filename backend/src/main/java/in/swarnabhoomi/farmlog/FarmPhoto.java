package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Column(name = "marketplace_public")
    private Boolean marketplacePublic = false;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

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
    public boolean isMarketplacePublic() { return Boolean.TRUE.equals(marketplacePublic); }
    public void setMarketplacePublic(boolean value) { marketplacePublic = value; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
}
