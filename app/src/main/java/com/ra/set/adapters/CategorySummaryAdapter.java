package com.ra.set.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ra.set.R;
import com.ra.set.models.CategorySummary;

import java.util.List;
import java.util.Locale;

public class CategorySummaryAdapter extends RecyclerView.Adapter<CategorySummaryAdapter.ViewHolder> {

    private final Context context;
    private final List<CategorySummary> items;

    public CategorySummaryAdapter(Context context, List<CategorySummary> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_category_summary, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategorySummary cs = items.get(position);
        holder.tvCategoryName.setText(cs.getCategory());
        holder.tvCategoryCount.setText(String.format(Locale.getDefault(), "%d transactions", cs.getCount()));
        holder.tvCategoryTotal.setText(context.getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", cs.getTotal())));

        // set icon as first letter
        if (cs.getCategory() != null && !cs.getCategory().isEmpty()) {
            holder.tvCategoryIcon.setText(cs.getCategory().substring(0,1).toUpperCase());
        } else {
            holder.tvCategoryIcon.setText("?");
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryIcon, tvCategoryName, tvCategoryCount, tvCategoryTotal;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryIcon = itemView.findViewById(R.id.tvCategoryIcon);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryCount = itemView.findViewById(R.id.tvCategoryCount);
            tvCategoryTotal = itemView.findViewById(R.id.tvCategoryTotal);
        }
    }
}

