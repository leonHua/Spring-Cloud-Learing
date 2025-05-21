package io.seata.sample.storageservice;

// A simple Storage entity (can be expanded as needed)
// In a real application, this would likely be a JPA entity or similar.
public class Storage {
    private Long id;
    private String commodityCode;
    private Integer count; // Represents available stock

    public Storage() {}

    public Storage(String commodityCode, Integer count) {
        this.commodityCode = commodityCode;
        this.count = count;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCommodityCode() {
        return commodityCode;
    }

    public void setCommodityCode(String commodityCode) {
        this.commodityCode = commodityCode;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
