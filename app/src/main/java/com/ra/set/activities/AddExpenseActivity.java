package com.ra.set.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.ra.set.R;
import com.ra.set.models.Expense;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AddExpenseActivity extends AppCompatActivity {

    private static final String TAG = "AddExpenseActivity";

    private EditText etAmount, etDate, etNote;
    private Spinner spCategory;
    private MaterialButton btnSave;

    // Realtime Database reference for user's expenses
    private DatabaseReference expensesRef;

    private final SimpleDateFormat uiDateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        etAmount = findViewById(R.id.etAmount);
        etDate = findViewById(R.id.etDate);
        etNote = findViewById(R.id.etNote);
        spCategory = findViewById(R.id.spCategory);
        btnSave = findViewById(R.id.btnSave);

        // basic auth check
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        // Use Realtime Database under /users/{uid}/expenses
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        expensesRef = db.getReference("users").child(uid).child("expenses");

        setupCategorySpinner();
        setupDatePicker();

        btnSave.setOnClickListener(v -> onSave());
    }

    private void setupCategorySpinner() {
        // load categories from resources so they are easy to change/localize
        String[] cats = getResources().getStringArray(R.array.expense_categories);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cats);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(adapter);
    }

    private void setupDatePicker() {
        etDate.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            DatePickerDialog dp = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(year, month, dayOfMonth, 0, 0, 0);
                etDate.setText(uiDateFmt.format(sel.getTime()));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            dp.show();
        });
    }

    private void onSave() {
        String amountStr = etAmount.getText().toString().trim();
        String dateStr = etDate.getText().toString().trim();
        String note = etNote.getText().toString().trim();
        String category = (String) spCategory.getSelectedItem();

        if (TextUtils.isEmpty(amountStr)) {
            etAmount.setError(getString(R.string.required_field));
            etAmount.requestFocus();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            etAmount.setError(getString(R.string.invalid_amount));
            etAmount.requestFocus();
            return;
        }

        // disable save to avoid double taps
        btnSave.setEnabled(false);

        long timestamp = System.currentTimeMillis();
        // if date provided, try to parse and use midnight of that date
        if (!TextUtils.isEmpty(dateStr)) {
            try {
                Date d = uiDateFmt.parse(dateStr);
                if (d != null) timestamp = d.getTime();
            } catch (ParseException ignored) { }
        }

        // create expense object
        Expense expense = new Expense(amount, category, dateStr, note, timestamp);

        // create new node in Realtime Database under users/{uid}/expenses
        DatabaseReference newRef = expensesRef.push();
        String id = newRef.getKey();
        expense.setId(id);

        newRef.setValue(expense)
                .addOnSuccessListener(aVoid -> {
                    // Update precomputed total safely using a transaction
                    DatabaseReference totalRef = FirebaseDatabase.getInstance()
                            .getReference("users").child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                            .child("summaries").child("global").child("total");

                    double toAdd = amount;
                    totalRef.runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            Double cur = null;
                            Object v = currentData.getValue();
                            if (v instanceof Number) cur = ((Number) v).doubleValue();
                            else if (v instanceof String) {
                                try { cur = Double.parseDouble((String) v); } catch (NumberFormatException ignored) { }
                            }

                            double next = (cur == null) ? toAdd : cur + toAdd;
                            currentData.setValue(next);
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                            if (error != null) {
                                Log.e(TAG, "Failed to update summary total", error.toException());
                                Toast.makeText(AddExpenseActivity.this, "Expense saved but failed to update total: " + error.getMessage(), Toast.LENGTH_LONG).show();
                                setResult(RESULT_OK);
                                finish();
                                return;
                            }

                            Toast.makeText(AddExpenseActivity.this, getString(R.string.expense_added), Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        }
                    });

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to write expense", e);
                    Toast.makeText(AddExpenseActivity.this, getString(R.string.expense_failed) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnSave.setEnabled(true);
                });
    }
}
