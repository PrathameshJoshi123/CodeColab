package com.example.responsiveui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.responsiveui.api.models.MatchRequestResponse;
import java.util.List;

/**
 * ==================== MyRequestsAdapter ====================
 * RecyclerView adapter for displaying user's own match requests
 * Shows status (pending, accepted, rejected) and allows cancellation
 */
public class MyRequestsAdapter extends RecyclerView.Adapter<MyRequestsAdapter.MyRequestViewHolder> {
    
    private final List<MatchRequestResponse> requests;
    private final Context context;
    private MyRequestActionListener listener;

    public interface MyRequestActionListener {
        void onCancel(MatchRequestResponse request);
    }

    public MyRequestsAdapter(List<MatchRequestResponse> requests, Context context) {
        this.requests = requests;
        this.context = context;
    }

    public void setListener(MyRequestActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public MyRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_request_card, parent, false);
        return new MyRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyRequestViewHolder holder, int position) {
        MatchRequestResponse request = requests.get(position);
        holder.bind(request, listener);
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    public void updateRequests(List<MatchRequestResponse> newRequests) {
        requests.clear();
        requests.addAll(newRequests);
        notifyDataSetChanged();
    }

    public void updateMatches(List<MatchRequestResponse> newRequests) {
        updateRequests(newRequests);
    }

    // ==================== ViewHolder ====================
    
    static class MyRequestViewHolder extends RecyclerView.ViewHolder {
        private TextView sessionTypeText;
        private TextView messageText;
        private TextView statusBadge;
        private TextView createdAtText;
        private Button btnCancel;

        public MyRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            
            sessionTypeText = itemView.findViewById(R.id.sessionTypeText);
            messageText = itemView.findViewById(R.id.messageText);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            createdAtText = itemView.findViewById(R.id.createdAtText);
            btnCancel = itemView.findViewById(R.id.btnCancel);
        }

        public void bind(MatchRequestResponse request, MyRequestActionListener listener) {
            // Session type and message
            sessionTypeText.setText(request.sessionType);
            messageText.setText(request.message);

            // Status badge with drawable
            String status = request.status != null ? request.status : "pending";
            statusBadge.setText(status.substring(0, 1).toUpperCase() + status.substring(1));
            
            int statusDrawable;
            switch (status.toLowerCase()) {
                case "accepted":
                    statusDrawable = R.drawable.bg_status_accepted;
                    break;
                case "rejected":
                    statusDrawable = R.drawable.bg_status_rejected;
                    break;
                default: // pending
                    statusDrawable = R.drawable.bg_status_pending;
                    break;
            }
            statusBadge.setBackground(itemView.getContext().getDrawable(statusDrawable));

            // Created at date
            if (request.createdAt != null) {
                createdAtText.setText("Created: " + request.createdAt);
            }

            // Cancel button visibility and action
            boolean canCancel = "pending".equals(status.toLowerCase());
            btnCancel.setVisibility(canCancel ? View.VISIBLE : View.GONE);
            
            btnCancel.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCancel(request);
                }
            });
        }
    }
}
