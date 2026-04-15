package com.example.responsiveui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.responsiveui.api.models.SprintTodoResponse;
import java.util.List;

/**
 * ==================== SprintTodoAdapter ====================
 * RecyclerView adapter for displaying and managing sprint todos
 * Features:
 * - Display todo title and description
 * - Toggle completion status
 * - Delete todos
 * - Notify completion count changes
 */
public class SprintTodoAdapter extends RecyclerView.Adapter<SprintTodoAdapter.TodoViewHolder> {
    
    private final List<SprintTodoResponse> todos;
    private final Context context;
    private TodoActionListener listener;

    public interface TodoActionListener {
        void onToggleComplete(SprintTodoResponse todo, int position);
        void onDeleteTodo(SprintTodoResponse todo, int position);
    }

    public SprintTodoAdapter(List<SprintTodoResponse> todos, Context context) {
        this.todos = todos;
        this.context = context;
    }

    public void setListener(TodoActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public TodoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sprint_todo, parent, false);
        return new TodoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TodoViewHolder holder, int position) {
        SprintTodoResponse todo = todos.get(position);
        holder.bind(todo, listener, position);
    }

    @Override
    public int getItemCount() {
        return todos.size();
    }

    public void updateTodos(List<SprintTodoResponse> newTodos) {
        todos.clear();
        todos.addAll(newTodos);
        notifyDataSetChanged();
    }

    public void addTodo(SprintTodoResponse todo) {
        todos.add(todo);
        notifyItemInserted(todos.size() - 1);
    }

    public void removeTodo(int position) {
        if (position >= 0 && position < todos.size()) {
            todos.remove(position);
            notifyItemRemoved(position);
        }
    }

    public int getCompletedCount() {
        int count = 0;
        for (SprintTodoResponse todo : todos) {
            if (todo.isCompleted) {
                count++;
            }
        }
        return count;
    }

    public int getTotalCount() {
        return todos.size();
    }

    // ==================== ViewHolder ====================
    
    static class TodoViewHolder extends RecyclerView.ViewHolder {
        private CheckBox cbTodoComplete;
        private TextView tvTodoTitle;
        private TextView tvTodoDescription;
        private ImageButton btnDeleteTodo;

        public TodoViewHolder(@NonNull View itemView) {
            super(itemView);
            
            cbTodoComplete = itemView.findViewById(R.id.cbTodoComplete);
            tvTodoTitle = itemView.findViewById(R.id.tvTodoTitle);
            tvTodoDescription = itemView.findViewById(R.id.tvTodoDescription);
            btnDeleteTodo = itemView.findViewById(R.id.btnDeleteTodo);
        }

        public void bind(SprintTodoResponse todo, TodoActionListener listener, int position) {
            // Set todo title
            tvTodoTitle.setText(todo.title != null ? todo.title : "Untitled");
            
            // Set todo description
            if (todo.description != null && !todo.description.isEmpty()) {
                tvTodoDescription.setText(todo.description);
                tvTodoDescription.setVisibility(View.VISIBLE);
            } else {
                tvTodoDescription.setVisibility(View.GONE);
            }
            
            // Set completion state
            cbTodoComplete.setChecked(todo.isCompleted);
            
            // Toggle completion
            cbTodoComplete.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleComplete(todo, position);
                }
            });
            
            // Delete button
            btnDeleteTodo.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteTodo(todo, position);
                }
            });
        }
    }
}
