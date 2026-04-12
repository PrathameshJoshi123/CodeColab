package com.example.responsiveui.api;

import android.content.Context;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * API Configuration and Retrofit client setup
 * Handles all API communication with the CodeCollab backend
 */
public class ApiConfig {
    
    // Backend URL - Configure based on your setup:
    // 
    // WiFi Debugging (Physical Device):
    //   1. Find your machine IP: On Windows CMD run: ipconfig
    //   2. Look for IPv4 Address (e.g., 192.168.x.x or 172.x.x.x)
    //   3. Update below to: http://<YOUR_IP>:8000/
    //   4. Example: http://192.168.1.100:8000/
    //
    // Android Emulator:
    //   Use: http://10.0.2.2:8000/
    //
    // Production:
    //   Use: https://your-domain.com
    //
    private static final String BASE_URL = "http://192.168.29.99:8000/"; // UPDATE TO YOUR MACHINE IP
    
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
        
        // Add JWT token interceptor for backend authentication
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
