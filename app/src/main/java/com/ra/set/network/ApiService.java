package com.ra.set.network;


import com.ra.set.models.Expense;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.DELETE;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import com.ra.set.models.LoginRequest;


import retrofit2.http.Body;
import retrofit2.http.POST;
public interface ApiService {

    @POST("auth/login")
    Call<Void> login(@Body LoginRequest request);

    @GET("expenses")
    Call<List<Expense>> getExpenses();

    @POST("expenses/create")
    Call<Void> addExpense(@Body Expense expense);

    @PUT("expenses/update/{id}")
    Call<Void> updateExpense(@Path("id") int id, @Body Expense expense);

    @DELETE("expenses/delete/{id}")
    Call<Void> deleteExpense(@Path("id") int id);
}
