package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class FarmEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private LocalDate entryDate;
    private String weather, pincode, farmSection, workDone, materials, cropCondition, soilNotes, tomorrowPlan, specialNote;
    private String seasonName, applicationKind, applicationName, applicationUnit;
    private String pestDisease, issueSeverity, intervention;
    private Integer workerCount;
    private Double laborCost, materialsCost, otherCost, productionKg, soldKg, saleIncome;
    private Double plotAreaAcres, waterLitres, applicationAmount;
    private java.time.LocalDate followUpDate;
    private String photoUrl;
    @Column(length=3000) private String weatherDetails;
    @Column(length=5000) private String receiptPhotoUrls;
    @Column(unique=true, length=80) private String clientRequestId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public LocalDate getEntryDate(){return entryDate;} public void setEntryDate(LocalDate v){entryDate=v;}
    public String getWeather(){return weather;} public void setWeather(String v){weather=v;}
    public String getPincode(){return pincode;} public void setPincode(String v){pincode=v;}
    public String getFarmSection(){return farmSection;} public void setFarmSection(String v){farmSection=v;}
    public String getWorkDone(){return workDone;} public void setWorkDone(String v){workDone=v;}
    public String getMaterials(){return materials;} public void setMaterials(String v){materials=v;}
    public Integer getWorkerCount(){return workerCount;} public void setWorkerCount(Integer v){workerCount=v;}
    public Double getLaborCost(){return laborCost;} public void setLaborCost(Double v){laborCost=v;}
    public Double getMaterialsCost(){return materialsCost;} public void setMaterialsCost(Double v){materialsCost=v;}
    public Double getOtherCost(){return otherCost;} public void setOtherCost(Double v){otherCost=v;}
    public Double getProductionKg(){return productionKg;} public void setProductionKg(Double v){productionKg=v;}
    public Double getSoldKg(){return soldKg;} public void setSoldKg(Double v){soldKg=v;}
    public Double getSaleIncome(){return saleIncome;} public void setSaleIncome(Double v){saleIncome=v;}
    public String getCropCondition(){return cropCondition;} public void setCropCondition(String v){cropCondition=v;}
    public String getSoilNotes(){return soilNotes;} public void setSoilNotes(String v){soilNotes=v;}
    public String getTomorrowPlan(){return tomorrowPlan;} public void setTomorrowPlan(String v){tomorrowPlan=v;}
    public String getSpecialNote(){return specialNote;} public void setSpecialNote(String v){specialNote=v;}
    public String getPhotoUrl(){return photoUrl;} public void setPhotoUrl(String v){photoUrl=v;}
    public String getWeatherDetails(){return weatherDetails;} public void setWeatherDetails(String v){weatherDetails=v;}
    public String getSeasonName(){return seasonName;} public void setSeasonName(String v){seasonName=v;}
    public Double getPlotAreaAcres(){return plotAreaAcres;} public void setPlotAreaAcres(Double v){plotAreaAcres=v;}
    public Double getWaterLitres(){return waterLitres;} public void setWaterLitres(Double v){waterLitres=v;}
    public String getApplicationKind(){return applicationKind;} public void setApplicationKind(String v){applicationKind=v;}
    public String getApplicationName(){return applicationName;} public void setApplicationName(String v){applicationName=v;}
    public Double getApplicationAmount(){return applicationAmount;} public void setApplicationAmount(Double v){applicationAmount=v;}
    public String getApplicationUnit(){return applicationUnit;} public void setApplicationUnit(String v){applicationUnit=v;}
    public String getPestDisease(){return pestDisease;} public void setPestDisease(String v){pestDisease=v;}
    public String getIssueSeverity(){return issueSeverity;} public void setIssueSeverity(String v){issueSeverity=v;}
    public String getIntervention(){return intervention;} public void setIntervention(String v){intervention=v;}
    public java.time.LocalDate getFollowUpDate(){return followUpDate;} public void setFollowUpDate(java.time.LocalDate v){followUpDate=v;}
    public String getReceiptPhotoUrls(){return receiptPhotoUrls;} public void setReceiptPhotoUrls(String v){receiptPhotoUrls=v;}
    public String getClientRequestId(){return clientRequestId;} public void setClientRequestId(String v){clientRequestId=v;}
    public AppUser getOwner(){return owner;} public void setOwner(AppUser v){owner=v;}
}
