package com.example.responsiveui.api;

import android.content.Context;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * API Configuration and Retrofit client setup
 * Handles all API communication with the CodeCollab backend
 */
public class ApiConfig {
    
    // Backend URL - Update this to match your deployment
    // For local development: http://10.0.2.2:8000 (Android emulator)
    // For physical device: http://<YOUR_IP>:8000
    // For production: https://your-domain.com
    private static final String BASE_URL = "http://10.0.2.2:8000/";
    
    private static Retrofit retrofit;
    private static OkHttpClient okHttpClient;
    
    /**
     * Initialize and get Retrofit instance
     */
    public static Retrofit getRetrofitInstance(Context context) {
        if (retrofit == null) {
            okHttpClient = buildOkHttpClient(context);
            
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
    
    /**
     * Build OkHttpClient with interceptors
     */
    private static OkHttpClient buildOkHttpClient(Context context) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        
        // Add logging interceptor for debugging
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        builder.addInterceptor(loggingInterceptor);
        
        // Add Firebase JWT token interceptor
        builder.addInterceptor(new TokenInterceptor());
        
        return builder.build();
    }
    
    /**
     * Get CodeCollabApiService instance
     */
    public static CodeCollabApiService getApiService(Context context) {
        return getRetrofitInstance(context).create(CodeCollabApiService.class);
    }
    
    /**
     * Update the base URL (useful for changing environments)
     * Note: Retrofit caches the instance, so create a new one when needed
     */
    public static void setBaseUrl(String newBaseUrl) {
        retrofit = null;
    }
}
