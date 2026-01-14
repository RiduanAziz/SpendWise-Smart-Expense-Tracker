package com.ra.set.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ra.set.R;
import com.ra.set.models.Expense;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {

    private final Context context;
    private final List<Expense> expenseList;

    // click listener
    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(Expense expense);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public ExpenseAdapter(Context context, List<Expense> expenseList) {
        this.context = context;
        this.expenseList = expenseList;
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_expense, parent, false);
        return new ExpenseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense expense = expenseList.get(position);

        // Category
        holder.tvCategory.setText(expense.getCategory());

        // Date (from timestamp if available) or fallback to string date
        if (expense.getTimestamp() > 0) {
            holder.tvDate.setText(formatDate(expense.getTimestamp()));
        } else if (expense.getDate() != null && !expense.getDate().isEmpty()) {
            holder.tvDate.setText(expense.getDate());
        } else {
            holder.tvDate.setText("");
        }

        // Amount
        String formatted = String.format(Locale.getDefault(), "%.2f", expense.getAmount());
        // Use resource string to allow translations and avoid concatenation in setText
        holder.tvAmount.setText(context.getString(R.string.amount_negative_format, formatted));

        // Category Icon (First letter)
        if (expense.getCategory() != null && !expense.getCategory().isEmpty()) {
            holder.tvCategoryIcon.setText(
                    expense.getCategory().substring(0, 1).toUpperCase()
            );
        } else {
            holder.tvCategoryIcon.setText("?");
        }

        // item click
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) onItemClickListener.onItemClick(expense);
        });
    }

    @Override
    public int getItemCount() {
        return expenseList.size();
    }

    /* =======================
       ViewHolder
       ======================= */
    public static class ExpenseViewHolder extends RecyclerView.ViewHolder {

        TextView tvCategoryIcon, tvCategory, tvDate, tvAmount;

        ExpenseViewHolder(@NonNull View itemView) {
            super(itemView);

            tvCategoryIcon = itemView.findViewById(R.id.tvCategoryIcon);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvAmount = itemView.findViewById(R.id.tvAmount);
        }
    }

    /* =======================
       Utilities
       ======================= */

    private String formatDate(long timestamp) {
        if (timestamp <= 0) return "";

        SimpleDateFormat sdf =
                new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
