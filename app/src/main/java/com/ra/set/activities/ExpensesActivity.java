package com.ra.set.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.widget.ImageView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.DatePicker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.ra.set.R;
import com.ra.set.adapters.ExpenseAdapter;
import com.ra.set.models.Expense;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ExpensesActivity extends AppCompatActivity {

    /* =======================
       UI Components
       ======================= */
    private RecyclerView recyclerView;
    private EditText etSearchBox;
    private android.widget.Spinner spCategoryFilter;
    private TextView tvDateFilterView;
    private boolean spIgnoreEvents = false;

    /* =======================
       Bottom Navigation
       ======================= */
    // Use View to avoid layout-cast issues (bottom_nav defines TextView items inside a parent layout)
    private View navDashboard, navExpenses, navReports, navExport;
    private View navAdd;

    /* =======================
       Data
       ======================= */
    private ExpenseAdapter adapter;
    private final List<Expense> expenseList = new ArrayList<>();
    // master list loaded from Firebase (unfiltered)
    private final List<Expense> allExpenses = new ArrayList<>();

    // Keep query and listener as fields so we can remove the listener in onDestroy
    private Query expensesQuery;
    private ValueEventListener expensesListener;

    // current filters
    private String currentSearch = "";
    private String currentCategory = "All";
    private Long currentDateFilter = null; // midnight millis or null

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expenses);

        initViews();
        setupRecycler();
        setupFirebaseListener();
        setupBottomNavigation();

        // wire search and filters
        setupFilters();
    }

    /* =======================
       Init Views
       ======================= */
    private void initViews() {
        recyclerView = findViewById(R.id.recyclerRecent);
        etSearchBox = findViewById(R.id.search_box);
        spCategoryFilter = findViewById(R.id.spCategoryFilter);
        tvDateFilterView = findViewById(R.id.tvDateFilter);

        // find bottom navigation items via the included bottom_nav root to avoid implicit-cast warnings
        View bottomNavRoot = findViewById(R.id.bottom_nav);
        if (bottomNavRoot != null) {
            navDashboard = bottomNavRoot.findViewById(R.id.navDashboard);
            navExpenses = bottomNavRoot.findViewById(R.id.navExpenses);
            navAdd = bottomNavRoot.findViewById(R.id.navAdd);
            navReports = bottomNavRoot.findViewById(R.id.navReports);
            navExport = bottomNavRoot.findViewById(R.id.navExport);
        } else {
            // fallback
            navDashboard = findViewById(R.id.navDashboard);
            navExpenses = findViewById(R.id.navExpenses);
            navAdd = findViewById(R.id.navAdd);
            navReports = findViewById(R.id.navReports);
            navExport = findViewById(R.id.navExport);
        }

        // quick handlers
        TextView tvAddLabel = findViewById(R.id.tvAddLabel);
        if (tvAddLabel != null) {
            tvAddLabel.setOnClickListener(v -> startActivity(new Intent(ExpensesActivity.this, AddExpenseActivity.class)));
        }
        // setup profile popup (View Profile / Logout) - mirrors Dashboard
        setupProfileMenu();
    }

    private void setupProfileMenu() {
        ImageView imgProfile = findViewById(R.id.imgProfile);
        if (imgProfile == null) return;

        imgProfile.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(ExpensesActivity.this, imgProfile);
            popupMenu.getMenuInflater().inflate(R.menu.menu_profile, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_view_profile) {
                    try {
                        startActivity(new Intent(ExpensesActivity.this, ProfileActivity.class));
                    } catch (Exception e) {
                        android.util.Log.e("ExpensesActivity", "Failed to open ProfileActivity", e);
                    }
                    return true;
                }

                if (id == R.id.menu_logout) {
                    logoutUser();
                    return true;
                }

                return false;
            });

            popupMenu.show();
        });
    }

    private void logoutUser() {
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(ExpensesActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupFilters() {
        if (etSearchBox != null) {
            etSearchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    currentSearch = s == null ? "" : s.toString().trim();
                    applyFiltersLocally();
                }
            });
        }

        // Spinner listener: update category filter when user chooses a category
        if (spCategoryFilter != null) {
            spCategoryFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (spIgnoreEvents) return;
                    Object sel = parent.getItemAtPosition(position);
                    currentCategory = sel == null ? "All" : String.valueOf(sel);
                    applyFiltersLocally();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
        }

        if (tvDateFilterView != null) {
            tvDateFilterView.setOnClickListener(v -> showDatePicker());
        }
    }

    /* =======================
       RecyclerView Setup
       ======================= */
    private void setupRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpenseAdapter(this, expenseList);
        // open on click
        adapter.setOnItemClickListener(expense -> {
            if (expense.getId() == null || expense.getId().isEmpty()) return;
            Intent i = new Intent(ExpensesActivity.this, com.ra.set.activities.EditExpenseActivity.class);
            i.putExtra(com.ra.set.activities.EditExpenseActivity.EXTRA_EXPENSE_ID, expense.getId());
            startActivity(i);
        });
        recyclerView.setAdapter(adapter);
    }

    /* =======================
       Realtime Database Listener
       ======================= */
    private void setupFirebaseListener() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        // Make tvTotalAmount and tvEmpty local variables captured by the listener
        TextView tvTotalAmount = findViewById(R.id.tvTotalAmount);
        TextView tvEmpty = findViewById(R.id.tvEmpty);

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        // Use Realtime Database under path: users/{uid}/expenses
        DatabaseReference expensesRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("expenses");
        // Query ordered by timestamp (ascending) - we'll sort descending locally to match Firestore order
        expensesQuery = expensesRef.orderByChild("timestamp");

        expensesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExpenses.clear();
                double total = 0;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Expense expense = ds.getValue(Expense.class);
                    if (expense == null) continue;
                    // Ensure id is populated so edit/delete can reference the correct key
                    expense.setId(ds.getKey());
                    allExpenses.add(expense);
                    total += expense.getAmount();
                }

                // Sort master list by timestamp descending
                allExpenses.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                if (allExpenses.isEmpty()) {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }

                if (tvTotalAmount != null) {
                    tvTotalAmount.setText(
                            getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", total))
                    );
                }

                // After loading master list, populate category spinner and apply filters
                populateCategorySpinner();
                applyFiltersLocally();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // optional: handle error
            }
        };

        expensesQuery.addValueEventListener(expensesListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (expensesQuery != null && expensesListener != null) {
            expensesQuery.removeEventListener(expensesListener);
        }
    }

    /* =======================
       Bottom Navigation
       ======================= */
    private void setupBottomNavigation() {

        if (navDashboard != null) navDashboard.setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });

        if (navExpenses != null) navExpenses.setOnClickListener(v -> {
            // Already on Expenses
        });

        if (navAdd != null) navAdd.setOnClickListener(v -> startActivity(new Intent(this, AddExpenseActivity.class)));

        if (navReports != null) navReports.setOnClickListener(v -> {
            startActivity(new Intent(this, ReportsActivity.class));
            finish();
        });

        if (navExport != null) navExport.setOnClickListener(v -> {
            startActivity(new Intent(this, ExportActivity.class));
            finish();
        });
    }

    private void showDatePicker() {
        final Calendar now = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                Calendar sel = Calendar.getInstance();
                sel.set(year, month, dayOfMonth, 0, 0, 0);
                sel.set(Calendar.MILLISECOND, 0);
                currentDateFilter = sel.getTimeInMillis();
                java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
                tvDateFilterView.setText(df.format(new java.util.Date(currentDateFilter)));
                applyFiltersLocally();
            }
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dp.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Clear", (dialog, which) -> {
            currentDateFilter = null;
            tvDateFilterView.setText("All dates");
        });
        dp.show();
    }

    private void populateCategorySpinner() {
        if (spCategoryFilter == null) return;
        final java.util.LinkedHashSet<String> cats = new java.util.LinkedHashSet<>();
        cats.add("All");
        for (Expense e : allExpenses) {
            if (e.getCategory() != null && !e.getCategory().isEmpty()) cats.add(e.getCategory());
        }
        final java.util.List<String> items = new java.util.ArrayList<>(cats);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // prevent firing listener on initial selection
        spIgnoreEvents = true;
        spCategoryFilter.setAdapter(adapter);
        int index = items.indexOf(currentCategory);
        spCategoryFilter.setSelection(index >= 0 ? index : 0);
        spIgnoreEvents = false;
    }

    private void applyFiltersLocally() {
        runOnUiThread(() -> {
            List<Expense> filtered = new ArrayList<>();
            for (Expense e : allExpenses) {
                boolean matchesSearch = true;
                if (currentSearch != null && !currentSearch.isEmpty()) {
                    String note = e.getNote() == null ? "" : e.getNote();
                    matchesSearch = note.toLowerCase(Locale.getDefault()).contains(currentSearch.toLowerCase(Locale.getDefault()));
                }

                boolean matchesCategory = true;
                if (currentCategory != null && !currentCategory.equals("All")) {
                    String cat = e.getCategory() == null ? "" : e.getCategory();
                    matchesCategory = cat.equals(currentCategory);
                }

                boolean matchesDate = true;
                if (currentDateFilter != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(e.getTimestamp());
                    Calendar sel = Calendar.getInstance();
                    sel.setTimeInMillis(currentDateFilter);
                    matchesDate = cal.get(Calendar.YEAR) == sel.get(Calendar.YEAR)
                            && cal.get(Calendar.DAY_OF_YEAR) == sel.get(Calendar.DAY_OF_YEAR);
                }

                if (matchesSearch && matchesCategory && matchesDate) filtered.add(e);
            }

            // sort filtered by timestamp desc
            filtered.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

            // replace adapter data (keep master list intact)
            expenseList.clear();
            expenseList.addAll(filtered);
            adapter.notifyDataSetChanged();
        });
    }

}
