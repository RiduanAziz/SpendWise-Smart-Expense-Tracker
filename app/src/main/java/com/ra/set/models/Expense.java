package com.ra.set.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.gson.annotations.SerializedName;

@IgnoreExtraProperties
public class Expense {

    @SerializedName("id")
    private String id;          // Firebase key or DB id

    @SerializedName("amount")
    private double amount;

    @SerializedName("category")
    private String category;

    @SerializedName("date")
    private String date;        // yyyy-MM-dd (optional display)

    @SerializedName("note")
    private String note;

    @SerializedName("timestamp")
    private long timestamp;     // REQUIRED for filtering (today/month)

    /* =======================
       Required empty constructor
       (Firebase + Gson)
       ======================= */
    public Expense() {
    }

    /* =======================
       Constructor for adding expense
       ======================= */
    public Expense(double amount, String category, String date, String note, long timestamp) {
        this.amount = amount;
        this.category = category;
        this.date = date;
        this.note = note;
        this.timestamp = timestamp;
    }

    /* =======================
       Getters
       ======================= */

    public String getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public String getDate() {
        return date;
    }

    public String getNote() {
        return note;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /* =======================
       Setters
       ======================= */

    public void setId(String id) {
        this.id = id;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
