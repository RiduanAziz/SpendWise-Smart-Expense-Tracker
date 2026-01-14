package com.ra.set.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ra.set.R;
import com.ra.set.models.Expense;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.FileOutputStream;
import java.io.IOException;


public class ExportActivity extends AppCompatActivity {

    private static final String TAG = "ExportActivity";

    private Spinner spinnerMonth, spinnerYear;

    private int selectedMonth = -1;
    private int selectedYear;

    // Bottom navigation views
    private View navDashboard, navExpenses, navAdd, navReports, navExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        spinnerMonth = findViewById(R.id.spinnerMonth);
        spinnerYear = findViewById(R.id.spinnerYear);

        setupSpinners();

        // btnCSV and btnPDF are only used here; make them local to avoid unused-field warning
        Button btnCSVLocal = findViewById(R.id.btnExportCSV);
        Button btnPDFLocal = findViewById(R.id.btnExportPDF);

        btnCSVLocal.setOnClickListener(v -> exportCSV());
        btnPDFLocal.setOnClickListener(v -> exportPDF());

        // wire bottom navigation
        setupBottomNavigation();

        // setup profile avatar popup (view profile, logout) — mirrors Dashboard behavior
        setupProfileMenu();
    }

    private void setupBottomNavigation() {
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

        if (navDashboard != null) navDashboard.setOnClickListener(v -> {
            startActivity(new Intent(ExportActivity.this, DashboardActivity.class));
            finish();
        });

        if (navExpenses != null) navExpenses.setOnClickListener(v -> {
            startActivity(new Intent(ExportActivity.this, ExpensesActivity.class));
            finish();
        });

        if (navAdd != null) navAdd.setOnClickListener(v -> {
            startActivity(new Intent(ExportActivity.this, AddExpenseActivity.class));
        });

        if (navReports != null) navReports.setOnClickListener(v -> {
            startActivity(new Intent(ExportActivity.this, ReportsActivity.class));
            finish();
        });

        // navExport is the current screen - no action, but keep clickable behavior for consistency
        if (navExport != null) navExport.setOnClickListener(v -> {
            // already on ExportActivity
        });
    }

    private void setupSpinners() {

        String[] months = {"All","Jan","Feb","Mar","Apr","May","Jun",
                "Jul","Aug","Sep","Oct","Nov","Dec"};
        spinnerMonth.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, months));

        List<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = currentYear; i >= currentYear - 5; i--) {
            years.add(String.valueOf(i));
        }
        spinnerYear.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, years));

        // replace undefined SimpleListener with proper OnItemSelectedListener
        spinnerMonth.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                selectedMonth = position - 1;
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // no-op
            }
        });

        spinnerYear.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                try {
                    selectedYear = Integer.parseInt(spinnerYear.getSelectedItem().toString());
                } catch (Exception e) {
                    selectedYear = currentYear;
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // no-op
            }
        });

        selectedYear = currentYear;
    }

    /* ================= CSV Export ================= */
    private void exportCSV() {

        fetchFilteredExpenses(expenses -> {
            try {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS),
                        "SET_Exports");

                if (!dir.exists()) {
                    boolean ok = dir.mkdirs();
                    if (!ok) Log.w(TAG, "Failed to create export directory: " + dir.getAbsolutePath());
                }

                File file = new File(dir, "expenses.csv");
                FileWriter writer = new FileWriter(file);

                writer.append("Date,Category,Amount,Note\n");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                for (Expense e : expenses) {
                    writer.append(sdf.format(new Date(e.getTimestamp()))).append(",");
                    writer.append(e.getCategory() == null ? "" : e.getCategory()).append(",");
                    writer.append(String.valueOf(e.getAmount())).append(",");
                    writer.append(e.getNote() == null ? "" : e.getNote()).append("\n");
                }

                writer.flush();
                writer.close();

                Toast.makeText(this, "CSV exported to Downloads/SET_Exports", Toast.LENGTH_LONG).show();

            } catch (Exception ex) {
                Log.e(TAG, "CSV export failed", ex);
                Toast.makeText(this, "CSV Export failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /* ================= PDF Export ================= */
    private void exportPDF() {

        fetchFilteredExpenses(expenses -> {

            if (expenses.isEmpty()) {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
                return;
            }

            try {

                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS),
                        "SET_Exports");

                if (!dir.exists()) {
                    boolean ok = dir.mkdirs();
                    if (!ok) Log.w(TAG, "Failed to create export directory: " + dir.getAbsolutePath());
                }

                String fileName = "Expense_Report_" + System.currentTimeMillis() + ".pdf";
                File file = new File(dir, fileName);

                // Create a simple PDF using PdfDocument (no external dependency)
                PdfDocument pdf = new PdfDocument();
                Paint paint = new Paint();
                Paint titlePaint = new Paint();
                titlePaint.setTextSize(16);
                titlePaint.setFakeBoldText(true);

                int pageWidth = 595; // A4-ish in points
                int pageHeight = 842;
                int margin = 40;
                int yStart = margin + 20;

                int pageNumber = 1;
                PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                Canvas canvas = page.getCanvas();

                // Header
                canvas.drawText("Smart Expense Tracker", margin, yStart, titlePaint);
                yStart += 24;
                paint.setTextSize(12);
                canvas.drawText("Expense Report", margin, yStart, paint);
                yStart += 20;

                String rangeText = (selectedMonth == -1)
                        ? "Year: " + selectedYear
                        : "Month: " + (selectedMonth + 1) + " / " + selectedYear;
                paint.setTextSize(10);
                canvas.drawText(rangeText, margin, yStart, paint);
                yStart += 20;

                // Table header
                int xCategory = margin + 100;
                int xAmount = xCategory + 180;
                int xNote = xAmount + 80;

                paint.setFakeBoldText(true);
                canvas.drawText("Date", margin, yStart, paint);
                canvas.drawText("Category", xCategory, yStart, paint);
                canvas.drawText("Amount", xAmount, yStart, paint);
                canvas.drawText("Note", xNote, yStart, paint);
                paint.setFakeBoldText(false);
                yStart += 16;

                double total = 0;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                for (Expense e : expenses) {
                    if (yStart > pageHeight - margin) {
                        pdf.finishPage(page);
                        pageNumber++;
                        page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                        canvas = page.getCanvas();
                        yStart = margin;
                    }

                    String date = sdf.format(new Date(e.getTimestamp()));
                    String category = e.getCategory() == null ? "" : e.getCategory();
                    String amountStr = String.format(Locale.getDefault(), "%.2f", e.getAmount());
                    String note = e.getNote() == null ? "" : e.getNote();

                    canvas.drawText(date, margin, yStart, paint);
                    canvas.drawText(category, xCategory, yStart, paint);
                    canvas.drawText(amountStr, xAmount, yStart, paint);
                    canvas.drawText(note, xNote, yStart, paint);

                    total += e.getAmount();
                    yStart += 14;
                }

                // Total
                yStart += 12;
                paint.setFakeBoldText(true);
                canvas.drawText("Total Expense: ₹ " + String.format(Locale.getDefault(), "%.2f", total), xAmount, yStart, paint);

                pdf.finishPage(page);

                // write to file
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    pdf.writeTo(fos);
                }
                pdf.close();

                Toast.makeText(this,
                        "PDF exported to Downloads/SET_Exports",
                        Toast.LENGTH_LONG).show();


            } catch (Exception e) {
                Log.e(TAG, "PDF export failed", e);
                Toast.makeText(this, "PDF export failed", Toast.LENGTH_SHORT).show();
            }
        });
    }


    /* ================= Realtime DB Fetch ================= */
    private void fetchFilteredExpenses(OnExpensesReady callback) {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in to export", Toast.LENGTH_LONG).show();
            callback.onReady(Collections.emptyList());
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("expenses");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Expense> result = new ArrayList<>();

                for (DataSnapshot doc : snapshot.getChildren()) {
                    Expense e = doc.getValue(Expense.class);
                    if (e == null) continue;

                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(e.getTimestamp());

                    boolean yearOk = c.get(Calendar.YEAR) == selectedYear;
                    boolean monthOk = selectedMonth == -1 ||
                            c.get(Calendar.MONTH) == selectedMonth;

                    if (yearOk && monthOk) result.add(e);
                }

                callback.onReady(result);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load expenses for export", error.toException());
                Toast.makeText(ExportActivity.this, "Failed to load expenses: " + error.getMessage(), Toast.LENGTH_LONG).show();
                callback.onReady(Collections.emptyList());
            }
        });
    }

    private void setupProfileMenu() {
        ImageView imgProfile = findViewById(R.id.imgProfile);
        if (imgProfile == null) return;

        imgProfile.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(ExportActivity.this, imgProfile);
            popupMenu.getMenuInflater().inflate(R.menu.menu_profile, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_view_profile) {
                    try {
                        startActivity(new Intent(ExportActivity.this, ProfileActivity.class));
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
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(ExportActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    interface OnExpensesReady {
        void onReady(List<Expense> expenses);
    }
}
