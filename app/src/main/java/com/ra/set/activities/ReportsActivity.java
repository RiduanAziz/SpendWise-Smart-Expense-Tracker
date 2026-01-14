package com.ra.set.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import android.graphics.Color; // added for pie chart label color
import android.widget.PopupMenu;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ra.set.R;
import com.ra.set.models.Expense;
import com.ra.set.models.CategorySummary;
import com.ra.set.adapters.CategorySummaryAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportsActivity extends AppCompatActivity {

    private static final String TAG = "ReportsActivity";

    private Spinner spinnerMonth, spinnerYear;
    private PieChart pieChart;
    private RecyclerView recyclerView;

    // Bottom nav views (use View to avoid implicit-cast layout warnings)
    private View navDashboard, navExpenses, navReports, navExport;
    private View navAdd; // center button

    private CategorySummaryAdapter adapter;
    private final List<CategorySummary> categoryList = new ArrayList<>();

    private int selectedMonth = -1; // -1 => All
    private int selectedYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        // Make activity consistent with others: require authenticated user
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.w(TAG, "User not authenticated - redirecting to Login");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initViews();
        setupSpinners();
        setupRecycler();
        setupBottomNavigation();
        fetchReports();
    }

    private void initViews() {
        spinnerMonth = findViewById(R.id.spinnerMonth);
        spinnerYear = findViewById(R.id.spinnerYear);
        pieChart = findViewById(R.id.pieChart);
        recyclerView = findViewById(R.id.recyclerReports);

        // bottom nav - find via included root
        View bottomNavRoot = findViewById(R.id.bottom_nav);
        if (bottomNavRoot != null) {
            navDashboard = bottomNavRoot.findViewById(R.id.navDashboard);
            navExpenses = bottomNavRoot.findViewById(R.id.navExpenses);
            navAdd = bottomNavRoot.findViewById(R.id.navAdd);
            navReports = bottomNavRoot.findViewById(R.id.navReports);
            navExport = bottomNavRoot.findViewById(R.id.navExport);
        } else {
            navDashboard = findViewById(R.id.navDashboard);
            navExpenses = findViewById(R.id.navExpenses);
            navAdd = findViewById(R.id.navAdd);
            navReports = findViewById(R.id.navReports);
            navExport = findViewById(R.id.navExport);
        }

        // setup profile popup (avatar) like Dashboard
        setupProfileMenu();
    }

    private void setupProfileMenu() {
        ImageView imgProfile = findViewById(R.id.imgProfile);
        if (imgProfile == null) return;

        imgProfile.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(ReportsActivity.this, imgProfile);
            popupMenu.getMenuInflater().inflate(R.menu.menu_profile, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_view_profile) {
                    try {
                        startActivity(new Intent(ReportsActivity.this, ProfileActivity.class));
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open ProfileActivity", e);
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
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) { }
        Intent intent = new Intent(ReportsActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CategorySummaryAdapter(this, categoryList);
        recyclerView.setAdapter(adapter);
        // Let outer layouts handle scrolling; default behavior is fine here.
    }

    private void setupSpinners() {
        String[] months = {"All", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, months);
        spinnerMonth.setAdapter(monthAdapter);

        List<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = currentYear; i >= currentYear - 5; i--) {
            years.add(String.valueOf(i));
        }
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, years);
        spinnerYear.setAdapter(yearAdapter);

        // Listeners
        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMonth = position - 1; // position 0 => All => -1
                fetchReports();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        spinnerYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    selectedYear = Integer.parseInt(spinnerYear.getSelectedItem().toString());
                } catch (Exception e) {
                    selectedYear = Calendar.getInstance().get(Calendar.YEAR);
                }
                fetchReports();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        selectedYear = Calendar.getInstance().get(Calendar.YEAR);
        // set spinner to current year selection safely
        try {
            int indexOfCurrentYear = yearAdapter.getPosition(String.valueOf(selectedYear));
            if (indexOfCurrentYear >= 0) spinnerYear.setSelection(indexOfCurrentYear);
        } catch (Exception ignored) { }
    }

    private void setupBottomNavigation() {
        if (navDashboard != null) {
            navDashboard.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(ReportsActivity.this, DashboardActivity.class));
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open DashboardActivity", e);
                }
            });
        }

        if (navExpenses != null) {
            navExpenses.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(ReportsActivity.this, ExpensesActivity.class));
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open ExpensesActivity", e);
                }
            });
        }

        if (navAdd != null) {
            navAdd.setOnClickListener(v -> startActivity(new Intent(ReportsActivity.this, AddExpenseActivity.class)));
        }

        if (navReports != null) {
            navReports.setOnClickListener(v -> {
                // Already on Reports
            });
        }

        if (navExport != null) {
            navExport.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(ReportsActivity.this, ExportActivity.class));
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open ExportActivity", e);
                }
            });
        }
    }

    private void fetchReports() {
        Log.d(TAG, "Fetching reports for year=" + selectedYear + " month=" + selectedMonth);
        // Guard against user sign-out during activity lifecycle
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.w(TAG, "User not authenticated when fetching reports");
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("expenses");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Aggregate per-category totals and counts
                Map<String, CategorySummary> summaryMap = new HashMap<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Expense e = child.getValue(Expense.class);
                    if (e == null) continue;
                    if (e.getId() == null) e.setId(child.getKey());

                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(e.getTimestamp());

                    boolean yearOk = c.get(Calendar.YEAR) == selectedYear;
                    int expenseMonth = c.get(Calendar.MONTH);
                    boolean monthOk = (selectedMonth == -1) || (expenseMonth == selectedMonth);

                    if (yearOk && monthOk) {
                        String cat = (e.getCategory() == null || e.getCategory().isEmpty()) ? "Other" : e.getCategory();
                        CategorySummary cs = summaryMap.get(cat);
                        if (cs == null) {
                            cs = new CategorySummary(cat, e.getAmount(), 1);
                        } else {
                            cs.setTotal(cs.getTotal() + e.getAmount());
                            cs.setCount(cs.getCount() + 1);
                        }
                        summaryMap.put(cat, cs);
                    }
                }

                // update recycler view with summaries
                categoryList.clear();
                categoryList.addAll(summaryMap.values());
                // sort by total desc
                categoryList.sort((a, b) -> Double.compare(b.getTotal(), a.getTotal()));

                adapter.notifyDataSetChanged();

                // prepare pie data
                Map<String, Float> pieMap = new HashMap<>();
                for (CategorySummary cs : categoryList) {
                    pieMap.put(cs.getCategory(), (float) cs.getTotal());
                }
                updatePie(pieMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Reports DB error", error.toException());
                Toast.makeText(ReportsActivity.this, "Failed to load reports: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updatePie(Map<String, Float> categoryMap) {
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Float> e : categoryMap.entrySet()) {
            String cat = e.getKey();
            Float val = e.getValue();
            entries.add(new PieEntry(val, cat));
        }

        if (entries.isEmpty()) {
            pieChart.clear();
            pieChart.invalidate();
            return;
        }

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(ColorTemplate.MATERIAL_COLORS);
        set.setDrawValues(false);

        // Enforce category/label color to #0F172A
        try {
            int catColor = Color.parseColor("#0F172A");
            // labels drawn on slices
            pieChart.setEntryLabelColor(catColor);
            // legend text color
            if (pieChart.getLegend() != null) pieChart.getLegend().setTextColor(catColor);
            // value text color (if draw values enabled later)
            set.setValueTextColor(catColor);
        } catch (IllegalArgumentException ignored) { }

        pieChart.setData(new PieData(set));
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(70f);
        pieChart.getLegend().setEnabled(false);
        pieChart.getDescription().setEnabled(false);
        pieChart.invalidate();
    }
}
