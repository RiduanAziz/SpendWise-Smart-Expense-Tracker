package com.ra.set.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ra.set.R;
import com.ra.set.models.Expense;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EditExpenseActivity extends AppCompatActivity {

    public static final String EXTRA_EXPENSE_ID = "expenseId";

    private EditText etAmount, etDate, etNote;
    private Spinner spCategory;

    private DatabaseReference expenseRef;
    private String expenseId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_expense);

        etAmount = findViewById(R.id.etAmount);
        spCategory = findViewById(R.id.spCategory);
        etDate = findViewById(R.id.etDate);
        etNote = findViewById(R.id.etNote);
        Button btnUpdate = findViewById(R.id.btnUpdate);
        Button btnDelete = findViewById(R.id.btnDelete);

        expenseId = getIntent().getStringExtra(EXTRA_EXPENSE_ID);
        if (expenseId == null || expenseId.isEmpty()) {
            Toast.makeText(this, "Missing expense id", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        expenseRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("expenses").child(expenseId);

        etDate.setOnClickListener(v -> showDatePicker());

        btnUpdate.setOnClickListener(v -> updateExpense());
        btnDelete.setOnClickListener(v -> confirmDelete());

        loadExpense();
    }

    private void loadExpense() {
        expenseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Expense e = snapshot.getValue(Expense.class);
                if (e == null) {
                    Toast.makeText(EditExpenseActivity.this, "Expense not found", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // populate fields
                etAmount.setText(String.format(Locale.getDefault(), "%.2f", e.getAmount()));

                // populate category spinner using the shared resource list (same as AddExpenseActivity)
                String[] cats = getResources().getStringArray(R.array.expense_categories);
                List<String> items = new ArrayList<>(Arrays.asList(cats));
                String cat = e.getCategory() == null ? "" : e.getCategory();
                // If the loaded category is not in the resource list, add it at position 0 so it's visible/selected
                if (!cat.isEmpty() && !items.contains(cat)) {
                    items.add(0, cat);
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(EditExpenseActivity.this, android.R.layout.simple_spinner_item, items);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spCategory.setAdapter(adapter);

                // select the loaded category if present
                int idx = items.indexOf(cat);
                spCategory.setSelection(Math.max(idx, 0));

                if (e.getTimestamp() > 0) {
                    DateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                    etDate.setText(df.format(new Date(e.getTimestamp())));
                    etDate.setTag(e.getTimestamp());
                } else {
                    etDate.setText(e.getDate());
                }
                etNote.setText(e.getNote());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditExpenseActivity.this, "Failed to load expense", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void showDatePicker() {
        final Calendar now = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar sel = Calendar.getInstance();
            sel.set(year, month, dayOfMonth, 0, 0, 0);
            sel.set(Calendar.MILLISECOND, 0);
            DateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            etDate.setText(df.format(sel.getTime()));
            etDate.setTag(sel.getTimeInMillis());
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dp.show();
    }

    private void updateExpense() {
        String sAmount = etAmount.getText().toString().trim();
        String category = spCategory.getSelectedItem() == null ? "" : spCategory.getSelectedItem().toString();
        String dateText = etDate.getText().toString().trim();
        String note = etNote.getText().toString().trim();

        if (sAmount.isEmpty()) {
            etAmount.setError("Enter amount");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(sAmount);
        } catch (NumberFormatException ex) {
            etAmount.setError("Invalid amount");
            return;
        }

        long timestamp = 0;
        Object tag = etDate.getTag();
        if (tag instanceof Long) timestamp = (Long) tag;

        // if user typed date manually, attempt parse
        if (timestamp == 0 && !dateText.isEmpty()) {
            try {
                DateFormat dfIn = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                Date parsed = dfIn.parse(dateText);
                if (parsed != null) timestamp = parsed.getTime();
            } catch (Exception ignored) { }
        }

        Expense updated = new Expense(amount, category, dateText, note, timestamp);
        updated.setId(expenseId);

        expenseRef.setValue(updated).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(EditExpenseActivity.this, "Updated", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(EditExpenseActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete))
                .setMessage(getString(R.string.confirm_delete_expense))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> deleteExpense())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void deleteExpense() {
        expenseRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(EditExpenseActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(EditExpenseActivity.this, "Delete failed", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
