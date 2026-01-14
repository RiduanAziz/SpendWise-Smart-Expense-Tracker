package com.ra.set.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.ra.set.R;
import com.ra.set.adapters.ExpenseAdapter;
import com.ra.set.models.Expense;
import com.ra.set.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.util.Log;
import java.util.Map;
import java.util.HashMap;



public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    /* =======================
       Dashboard UI
       ======================= */
    private TextView tvTotal, tvToday, tvMonth, tvDate;
    private TextView tvTodaySubtitle, tvMonthSubtitle;
    private TextView tvRecentEmpty, btnViewAll;
    private TextView tvOverviewTitle; // kept for compatibility but not used for welcome text
    private TextView tvWelcomeLabel, tvWelcomeName; // NEW: label and name
    private RecyclerView recyclerView;
    private PieChart pieChart;
    private ImageView imgProfile;

    /* =======================
       Bottom Navigation
       ======================= */

    // Updated to match res/layout/bottom_nav.xml where nav items are TextView
    // but use View here to avoid layout-cast issues in some build/tooling
    private android.view.View navDashboard, navExpenses, navReports, navExport;
    private android.view.View navAdd;

    /* =======================
       Data
       ======================= */
    private ExpenseAdapter adapter;
    private final List<Expense> expenseList = new ArrayList<>();
    private double computedTotal = 0.0;
    private boolean isComputedTotalReady = false;

    // Realtime Database refs
    private DatabaseReference expensesRef;
    private DatabaseReference totalRef;
    private DatabaseReference userRef;
    private String uid;
    private ValueEventListener expensesListener;
    private ValueEventListener summaryListener;
    private ValueEventListener userListener;
    private boolean hasSummaryTotal = false;
    private Double summaryTotal = null; // store numeric summary when available
    private ValueEventListener recentListener; // listener for latest 5
    private com.google.firebase.database.Query recentQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        initViews();
        setupRecycler();
        setupFirebase();
        loadDashboardData();
        setupBottomNavigation();

        // default selection
        selectNavItem(R.id.navDashboard);
    }

    /* =======================
       Init Views
       ======================= */
    private void initViews() {
        tvTotal = findViewById(R.id.tvTotalAmount);
        tvToday = findViewById(R.id.tvTodayAmount);
        tvMonth = findViewById(R.id.tvMonthAmount);
        tvDate = findViewById(R.id.tvCurrentDate);
        tvOverviewTitle = findViewById(R.id.tvOverviewTitle);

        // new welcome views
        tvWelcomeLabel = findViewById(R.id.tvWelcomeLabel);
        tvWelcomeName = findViewById(R.id.tvWelcomeName);

        // initialize default values to avoid empty UI while data loads
        tvTotal.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", 0.0)));
        tvToday.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", 0.0)));
        tvMonth.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", 0.0)));
        // default overview text until profile loads
        if (tvWelcomeLabel != null) tvWelcomeLabel.setText("Welcome,");
        if (tvWelcomeName != null) tvWelcomeName.setText("");

        recyclerView = findViewById(R.id.recyclerRecent);
        tvRecentEmpty = findViewById(R.id.tvRecentEmpty);
        btnViewAll = findViewById(R.id.btnViewAll);
        pieChart = findViewById(R.id.pieChart);
        tvTodaySubtitle = findViewById(R.id.tvTodaySubtitle);
        tvMonthSubtitle = findViewById(R.id.tvMonthSubtitle);

        // Use the included bottom_nav root to find child nav views (prevents layout-cast lint issues)
        android.view.View bottomNavRoot = findViewById(R.id.bottom_nav);
        if (bottomNavRoot != null) {
            navDashboard = bottomNavRoot.findViewById(R.id.navDashboard);
            navExpenses = bottomNavRoot.findViewById(R.id.navExpenses);
            navAdd = bottomNavRoot.findViewById(R.id.navAdd);
            navReports = bottomNavRoot.findViewById(R.id.navReports);
            navExport = bottomNavRoot.findViewById(R.id.navExport);
        } else {
            // fallback to direct lookup
            navDashboard = findViewById(R.id.navDashboard);
            navExpenses = findViewById(R.id.navExpenses);
            navAdd = findViewById(R.id.navAdd);
            navReports = findViewById(R.id.navReports);
            navExport = findViewById(R.id.navExport);
        }

        imgProfile = findViewById(R.id.imgProfile);
        setupProfileMenu();

        // View All -> open full expenses screen
        if (btnViewAll != null) {
            btnViewAll.setOnClickListener(v -> startActivity(new Intent(this, ExpensesActivity.class)));
        }

        tvDate.setText(getFormattedDate());
    }

    /* =======================
       RecyclerView Setup
       ======================= */
    private void setupRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpenseAdapter(this, expenseList);
        recyclerView.setAdapter(adapter);
        // small spacing between cards
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);
        recyclerView.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull android.view.View view, @NonNull androidx.recyclerview.widget.RecyclerView parent, @NonNull androidx.recyclerview.widget.RecyclerView.State state) {
                outRect.set(0, 0, 0, dp8);
            }
        });
    }

    /* =======================
       Firebase (Realtime DB) Setup
       ======================= */
    private void setupFirebase() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        expensesRef = db.getReference("users").child(uid).child("expenses");

        // Listen on the user node; prefer profile/name but fall back to users/{uid}/name
        userRef = db.getReference("users").child(uid);
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "User snapshot key=" + snapshot.getKey() + " exists=" + snapshot.exists()
                        + " children=" + snapshot.getChildrenCount() + " value=" + snapshot.getValue());

                String name = null;

                // 1) Prefer profile/name under /users/{uid}/profile/name
                try {
                    if (snapshot.hasChild("profile") && snapshot.child("profile").hasChild("name")) {
                        name = snapshot.child("profile").child("name").getValue(String.class);
                        Log.d(TAG, "snapshot.profile.name = " + name);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error reading profile/name", e);
                }

                // 2) Fallback to top-level /users/{uid}/name
                if (name == null || name.isEmpty()) {
                    try {
                        if (snapshot.hasChild("name")) {
                            name = snapshot.child("name").getValue(String.class);
                            Log.d(TAG, "snapshot.child('name') = " + name);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error reading child 'name' as String", e);
                    }
                }

                // 3) Check displayName under profile or top-level
                if (name == null || name.isEmpty()) {
                    try {
                        if (snapshot.hasChild("profile") && snapshot.child("profile").hasChild("displayName")) {
                            name = snapshot.child("profile").child("displayName").getValue(String.class);
                            Log.d(TAG, "snapshot.profile.displayName = " + name);
                        }
                        if ((name == null || name.isEmpty()) && snapshot.hasChild("displayName")) {
                            name = snapshot.child("displayName").getValue(String.class);
                            Log.d(TAG, "snapshot.child('displayName') = " + name);
                        }
                    } catch (Exception ignored) { }
                }

                // 4) If still empty, try reading the snapshot map for other keys
                if (name == null || name.isEmpty()) {
                    try {
                        Object value = snapshot.getValue();
                        if (value instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
                            if (map.containsKey("name") && map.get("name") != null) name = String.valueOf(map.get("name"));
                            if ((name == null || name.isEmpty()) && map.containsKey("displayName") && map.get("displayName") != null)
                                name = String.valueOf(map.get("displayName"));
                            if ((name == null || name.isEmpty()) && map.containsKey("fullName") && map.get("fullName") != null)
                                name = String.valueOf(map.get("fullName"));
                            if ((name == null || name.isEmpty()) && map.containsKey("username") && map.get("username") != null)
                                name = String.valueOf(map.get("username"));
                            // Also check nested profile map if present
                            if ((name == null || name.isEmpty()) && map.containsKey("profile") && map.get("profile") instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> profileMap = (java.util.Map<String, Object>) map.get("profile");
                                if (profileMap.containsKey("name") && profileMap.get("name") != null) name = String.valueOf(profileMap.get("name"));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error reading user map", e);
                    }
                }

                // guard against placeholder values like "$name"
                if (name != null && name.contains("$")) {
                    Log.w(TAG, "Profile 'name' contains placeholder-like value: " + name);
                    name = null;
                }

                // fallback to auth displayName
                if (name == null || name.isEmpty()) {
                    try {
                        if (FirebaseAuth.getInstance().getCurrentUser() != null)
                            name = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
                    } catch (Exception ignored) {}
                }

                // Final fallback: email local part
                if (name == null || name.isEmpty()) {
                    try {
                        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                        if (email != null && !email.isEmpty()) {
                            int at = email.indexOf('@');
                            name = (at > 0) ? email.substring(0, at) : email;
                        }
                    } catch (Exception ignored) {}
                }

                if (name == null) name = "";
                name = name.trim();
                if (!name.isEmpty()) {
                    name = name.substring(0,1).toUpperCase() + (name.length() > 1 ? name.substring(1) : "");
                }

                final String displayName = name.isEmpty() ? "" : name;
                Log.d(TAG, "Resolved displayName = '" + displayName + "'");
                runOnUiThread(() -> {
                    if (tvWelcomeName != null) tvWelcomeName.setText(displayName);
                    if (tvWelcomeLabel != null) tvWelcomeLabel.setText("Welcome,");
                    if (tvWelcomeName != null) tvWelcomeName.setVisibility(android.view.View.VISIBLE);
                    if (tvWelcomeLabel != null) tvWelcomeLabel.setVisibility(android.view.View.VISIBLE);
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Profile listener cancelled", error.toException());
            }
        };
        userRef.addValueEventListener(userListener);

        // summary total at /users/{uid}/summaries/global/total
        totalRef = db.getReference("users").child(uid).child("summaries").child("global").child("total");

        // one-time read to set initial total immediately (in case listener is delayed)
        totalRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Object val = snapshot.getValue();
                    Double parsed = null;
                    if (val instanceof Number) {
                        parsed = ((Number) val).doubleValue();
                    } else if (val instanceof String) {
                        try { parsed = Double.parseDouble((String) val); } catch (NumberFormatException ignored) {}
                    }

                    if (parsed != null) {
                        summaryTotal = parsed;
                        hasSummaryTotal = true;
                        tvTotal.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", summaryTotal)));
                    } else {
                        Log.d(TAG, "Initial summary present but 'total' not numeric");
                    }
                } else {
                    Log.d(TAG, "No initial summary value");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read initial summary", error.toException());
            }
        });

        // listen to summary changes
        summaryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Object val = snapshot.getValue();
                    Double parsed = null;
                    if (val instanceof Number) {
                        parsed = ((Number) val).doubleValue();
                    } else if (val instanceof String) {
                        try { parsed = Double.parseDouble((String) val); } catch (NumberFormatException ignored) {}
                    }

                    if (parsed != null) {
                        summaryTotal = parsed;
                        hasSummaryTotal = true;
                        tvTotal.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", parsed)));
                        Log.d(TAG, "Summary listener updated total: " + parsed);
                    } else {
                        summaryTotal = null;
                        hasSummaryTotal = false;
                        Log.d(TAG, "Summary listener: 'total' not numeric");
                        if (isComputedTotalReady) {
                            tvTotal.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", computedTotal)));
                        }
                    }
                } else {
                    summaryTotal = null;
                    hasSummaryTotal = false;
                    Log.d(TAG, "Summary listener: value does not exist");
                    if (isComputedTotalReady) {
                        tvTotal.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", computedTotal)));
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Summary listener error", error.toException());
                summaryTotal = null;
                hasSummaryTotal = false;
                if (isComputedTotalReady) {
                    tvTotal.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", computedTotal)));
                }
            }
        };

        totalRef.addValueEventListener(summaryListener);
    }

    /* =======================
       Load Dashboard Data
       ======================= */
    private void loadDashboardData() {
        // Listen for real-time updates ordered by timestamp descending
        Query q = expensesRef.orderByChild("timestamp");
        expensesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshots) {
                double total = 0;
                double today = 0;
                double month = 0;

                List<Expense> allExpenses = new ArrayList<>();
                // aggregate totals per category for the pie chart
                Map<String, Float> categoryMap = new HashMap<>();

                long todayStart = DateUtils.getTodayStart();
                long monthStart = DateUtils.getMonthStart();

                if (snapshots != null && snapshots.exists()) {
                    // snapshots is the parent node; iterate children (ascending by timestamp)
                    for (DataSnapshot child : snapshots.getChildren()) {
                        Expense eObj = child.getValue(Expense.class);
                        if (eObj == null) continue;

                        // ensure id is set (Realtime push key may be child.getKey())
                        if (eObj.getId() == null) eObj.setId(child.getKey());

                        total += eObj.getAmount();

                        if (eObj.getTimestamp() >= todayStart) {
                            today += eObj.getAmount();
                        }

                        if (eObj.getTimestamp() >= monthStart) {
                            month += eObj.getAmount();
                        }

                        allExpenses.add(eObj);
                        // accumulate per-category totals
                        String cat = (eObj.getCategory() == null || eObj.getCategory().isEmpty()) ? "Other" : eObj.getCategory();
                        float cur = categoryMap.containsKey(cat) ? categoryMap.get(cat) : 0f;
                        categoryMap.put(cat, cur + (float) eObj.getAmount());
                    }
                }

                // build PieEntry list from aggregated category totals
                List<PieEntry> pieEntries = new ArrayList<>();
                for (Map.Entry<String, Float> en : categoryMap.entrySet()) {
                    pieEntries.add(new PieEntry(en.getValue(), en.getKey()));
                }

                // allExpenses currently ascending — reverse to get most recent first
                allExpenses.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                computedTotal = total;
                isComputedTotalReady = true;

                // Only update UI if summary is NOT available
                if (!hasSummaryTotal) {
                    tvTotal.setText(getString(
                            R.string.amount_format,
                            String.format(Locale.getDefault(), "%.2f", computedTotal)
                    ));
                }
                tvToday.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", today)));
                tvMonth.setText(getString(R.string.amount_format, String.format(Locale.getDefault(), "%.2f", month)));

                // update subtitles: today's date and current month with year
                try {
                    java.text.SimpleDateFormat todayFmt = new java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                    java.text.SimpleDateFormat monthFmt = new java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault());
                    if (tvTodaySubtitle != null) tvTodaySubtitle.setText(todayFmt.format(new java.util.Date()));
                    if (tvMonthSubtitle != null) tvMonthSubtitle.setText(monthFmt.format(new java.util.Date()));
                } catch (Exception ignored) { }

                // Sort and show top 5 in dashboard recent list
                // expenseList.clear();
                // int maxRecent = 5;
                // for (int i = 0; i < Math.min(maxRecent, allExpenses.size()); i++) {
                //     expenseList.add(allExpenses.get(i));
                // }

                // Recent list is populated by `recentListener` (limitToLast(5)) to ensure
                // the RecyclerView always shows the latest 5 items in descending order.

                // empty state for recent
                if (expenseList.isEmpty()) {
                    if (tvRecentEmpty != null) tvRecentEmpty.setVisibility(android.view.View.VISIBLE);
                    recyclerView.setVisibility(android.view.View.GONE);
                } else {
                    if (tvRecentEmpty != null) tvRecentEmpty.setVisibility(android.view.View.GONE);
                    recyclerView.setVisibility(android.view.View.VISIBLE);
                }

                adapter.notifyDataSetChanged();
                setupPieChart(pieEntries);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Expenses listener error", error.toException());
                isComputedTotalReady = true;
            }
        };

        q.addValueEventListener(expensesListener);

        // New: listen only for the last 5 expenses by push-key so newly-created items
        // (push() keys) appear in the recent list immediately even if their timestamp
        // value is older (user-entered date). We still keep timestamp fallback when
        // keys are unavailable.
        recentQuery = expensesRef.orderByKey().limitToLast(5);
        recentListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshots) {
                List<Expense> recent = new ArrayList<>();
                if (snapshots != null && snapshots.exists()) {
                    for (DataSnapshot child : snapshots.getChildren()) {
                        Expense e = child.getValue(Expense.class);
                        if (e == null) continue;
                        if (e.getId() == null) e.setId(child.getKey());
                        recent.add(e);
                    }
                }
                // Prefer sorting by push-key (newer keys are lexicographically greater).
                // If keys are missing or equal, fall back to timestamp (newer timestamps first).
                recent.sort((a, b) -> {
                    try {
                        if (a.getId() != null && b.getId() != null) {
                            return b.getId().compareTo(a.getId());
                        }
                    } catch (Exception ignored) {}
                    return Long.compare(b.getTimestamp(), a.getTimestamp());
                });

                expenseList.clear();
                expenseList.addAll(recent);

                // empty state for recent
                if (expenseList.isEmpty()) {
                    if (tvRecentEmpty != null) tvRecentEmpty.setVisibility(android.view.View.VISIBLE);
                    if (recyclerView != null) recyclerView.setVisibility(android.view.View.GONE);
                } else {
                    if (tvRecentEmpty != null) tvRecentEmpty.setVisibility(android.view.View.GONE);
                    if (recyclerView != null) recyclerView.setVisibility(android.view.View.VISIBLE);
                }

                if (adapter != null) adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Recent listener cancelled", error.toException());
            }
        };

        recentQuery.addValueEventListener(recentListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (expensesRef != null && expensesListener != null) expensesRef.removeEventListener(expensesListener);
        if (totalRef != null && summaryListener != null) totalRef.removeEventListener(summaryListener);
        if (userRef != null && userListener != null) userRef.removeEventListener(userListener);
        if (recentQuery != null && recentListener != null) recentQuery.removeEventListener(recentListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh recent list when returning to Dashboard (e.g., after AddExpenseActivity)
        refreshRecent();
    }

    /**
     * One-time refresh of recent items using the existing recentQuery (orderByKey().limitToLast(5)).
     * This ensures a freshly added expense appears immediately when the user returns from add screen.
     */
    private void refreshRecent() {
        try {
            if (recentQuery == null) {
                if (expensesRef != null) recentQuery = expensesRef.orderByKey().limitToLast(5);
            }
            if (recentQuery == null) return;

            // Use a single-value read to avoid adding duplicate persistent listeners.
            recentQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshots) {
                    List<Expense> recent = new ArrayList<>();
                    if (snapshots != null && snapshots.exists()) {
                        for (DataSnapshot child : snapshots.getChildren()) {
                            Expense e = child.getValue(Expense.class);
                            if (e == null) continue;
                            if (e.getId() == null) e.setId(child.getKey());
                            recent.add(e);
                        }
                    }

                    // Sort newest push-key first, fallback to timestamp
                    recent.sort((a, b) -> {
                        try {
                            if (a.getId() != null && b.getId() != null) return b.getId().compareTo(a.getId());
                        } catch (Exception ignored) { }
                        return Long.compare(b.getTimestamp(), a.getTimestamp());
                    });

                    expenseList.clear();
                    expenseList.addAll(recent);
                    if (adapter != null) adapter.notifyDataSetChanged();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "refreshRecent cancelled", error.toException());
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to refresh recent list", e);
        }
    }
     private void setupProfileMenu() {

         imgProfile.setOnClickListener(v -> {

             PopupMenu popupMenu = new PopupMenu(this, imgProfile);
             popupMenu.getMenuInflater().inflate(R.menu.menu_profile, popupMenu.getMenu());

             popupMenu.setOnMenuItemClickListener(item -> {

                 int id = item.getItemId();

                 if (id == R.id.menu_view_profile) {
                     startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
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

        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /* =======================
       Bottom Navigation Logic
       ======================= */
    private void setupBottomNavigation() {

        if (navDashboard != null) {
            navDashboard.setOnClickListener(v -> {
                // Already on Dashboard - update selection
                selectNavItem(R.id.navDashboard);
            });
        } else {
            Log.w(TAG, "navDashboard is null - check bottom_nav include");
        }

        if (navExpenses != null) {
            navExpenses.setOnClickListener(v -> {
                selectNavItem(R.id.navExpenses);
                try {
                    startActivity(new Intent(this, ExpensesActivity.class));
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open ExpensesActivity", e);
                }
            });
        } else {
            Log.w(TAG, "navExpenses is null - check bottom_nav include");
        }

        if (navAdd != null) {
            navAdd.setOnClickListener(v -> {
                // highlight center button and open add screen
                selectNavItem(R.id.navAdd);
                try {
                    startActivity(new Intent(this, AddExpenseActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open AddExpenseActivity", e);
                }
            });
        } else {
            Log.w(TAG, "navAdd is null - check bottom_nav include");
        }

        if (navReports != null) {
            navReports.setOnClickListener(v -> {
                selectNavItem(R.id.navReports);
                try {
                    startActivity(new Intent(this, ReportsActivity.class));
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open ReportsActivity", e);
                }
            });
        } else {
            Log.w(TAG, "navReports is null - check bottom_nav include");
        }

        if (navExport != null) {
            navExport.setOnClickListener(v -> {
                selectNavItem(R.id.navExport);
                try {
                    startActivity(new Intent(this, ExportActivity.class));
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open ExportActivity", e);
                }
            });
        } else {
            Log.w(TAG, "navExport is null - check bottom_nav include");
        }
    }

    /**
     * Visually select a navigation item. This updates icon tints, label colors and
     * gives the center add button a subtle scale animation when active.
     */
    private void selectNavItem(int navId) {
        int activeColor = ContextCompat.getColor(this, R.color.accent_blue);
        int greyColor = Color.parseColor("#8A8A8A");

        // reset all to inactive (grey)
        if (navDashboard instanceof TextView) ((TextView) navDashboard).setTextColor(greyColor);
        if (navExpenses instanceof TextView) ((TextView) navExpenses).setTextColor(greyColor);
        if (navReports instanceof TextView) ((TextView) navReports).setTextColor(greyColor);
        if (navExport instanceof TextView) ((TextView) navExport).setTextColor(greyColor);

        // reset center button scale and lift
        if (navAdd != null) navAdd.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(150).start();

        // apply selected state
        if (navId == R.id.navDashboard) {
            if (navDashboard instanceof TextView) ((TextView) navDashboard).setTextColor(activeColor);
        } else if (navId == R.id.navExpenses) {
            if (navExpenses instanceof TextView) ((TextView) navExpenses).setTextColor(activeColor);
        } else if (navId == R.id.navReports) {
            if (navReports instanceof TextView) ((TextView) navReports).setTextColor(activeColor);
        } else if (navId == R.id.navExport) {
            if (navExport instanceof TextView) ((TextView) navExport).setTextColor(activeColor);
        } else if (navId == R.id.navAdd) {
            // highlight center add with scale and lift
            if (navAdd != null) navAdd.animate().scaleX(1.08f).scaleY(1.08f).translationY(-8f).setDuration(180).start();
        }
    }

    /* =======================
       Date Formatter
       ======================= */
    private String getFormattedDate() {
        // include year in the displayed date (e.g., MONDAY, JAN 12, 2026)
        SimpleDateFormat sdf =
                new SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date()).toUpperCase();
    }

    /* =======================
       Pie Chart Setup
       ======================= */
    private void setupPieChart(List<PieEntry> entries) {

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setDrawValues(false);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);

        // set category/legend text color to #0F172A
        try {
            int catColor = Color.parseColor("#0F172A");
            // labels drawn directly on slices
            pieChart.setEntryLabelColor(catColor);
            // legend text color
            if (pieChart.getLegend() != null) pieChart.getLegend().setTextColor(catColor);
            // values color (if enabled later)
            dataSet.setValueTextColor(catColor);
        } catch (IllegalArgumentException ignored) { }

        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(70f);
        pieChart.setTransparentCircleRadius(75f);
        pieChart.getLegend().setEnabled(false);
        pieChart.getDescription().setEnabled(false);

        pieChart.invalidate();
    }
}
