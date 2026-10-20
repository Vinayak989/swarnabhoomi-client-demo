package in.swarnabhoomi.farmlog;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class FarmEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private LocalDate entryDate;
    private String weather, pincode, farmSection, workDone, materials, cropCondition, soilNotes, tomorrowPlan, specialNote;
    private Integer workerCount;
    private Double laborCost, materialsCost, otherCost, productionKg, soldKg, saleIncome;
    private String photoUrl;
    @Column(length=3000) private String weatherDetails;

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
}
