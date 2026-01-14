package com.ra.set.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ra.set.R;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvUid, tvName, tvEmail, tvPassword, tvCreatedAt;
    private Button btnChangePassword, btnEditProfile;

    private DatabaseReference userRef;
    private ValueEventListener userListener;

    // bottom nav views
    private View navDashboard, navExpenses, navReports, navExport, navAdd;
    private ImageView imgProfile;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvUid = findViewById(R.id.tvUid);
        tvName = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPassword = findViewById(R.id.tvPassword);
        tvCreatedAt = findViewById(R.id.tvCreatedAt);

        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        // bind top-bar profile image (shared include)
        imgProfile = findViewById(R.id.imgProfile);

        // setup bottom navigation (find items inside included bottom_nav)
        setupBottomNavigation();
        // setup avatar popup menu
        setupProfileMenu();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        final String uid = user.getUid();
        tvUid.setText(uid);

        // show email from auth
        tvEmail.setText(user.getEmail());

        // password cannot be read; show placeholder
        tvPassword.setText("********");

        // try to get createdAt from DB; fallback to auth metadata
        userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // name: prefer profile.name then top-level name then auth displayName
                String name = null;
                try {
                    if (snapshot.hasChild("profile") && snapshot.child("profile").hasChild("name")) {
                        name = snapshot.child("profile").child("name").getValue(String.class);
                    }
                } catch (Exception ignored) { }

                if (name == null || name.isEmpty()) {
                    try {
                        if (snapshot.hasChild("name")) name = snapshot.child("name").getValue(String.class);
                    } catch (Exception ignored) { }
                }

                if (name == null || name.isEmpty()) {
                    String dn = user.getDisplayName();
                    if (dn != null && !dn.isEmpty()) name = dn;
                }

                if (name == null) name = "";
                tvName.setText(name);

                // createdAt: try common fields in DB
                Long createdAtMillis = null;
                try {
                    if (snapshot.hasChild("createdAt")) {
                        Object v = snapshot.child("createdAt").getValue();
                        if (v instanceof Long) createdAtMillis = (Long) v;
                        else if (v instanceof String) createdAtMillis = Long.parseLong((String) v);
                    }
                    if (createdAtMillis == null && snapshot.hasChild("profile") && snapshot.child("profile").hasChild("createdAt")) {
                        Object v = snapshot.child("profile").child("createdAt").getValue();
                        if (v instanceof Long) createdAtMillis = (Long) v;
                        else if (v instanceof String) createdAtMillis = Long.parseLong((String) v);
                    }
                } catch (Exception ignored) { }

                if (createdAtMillis == null) {
                    // fallback to auth user metadata
                    try {
                        if (user.getMetadata() != null) createdAtMillis = user.getMetadata().getCreationTimestamp();
                    } catch (Exception ignored) { }
                }

                if (createdAtMillis != null && createdAtMillis > 0) {
                    DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
                    tvCreatedAt.setText(df.format(new Date(createdAtMillis)));
                } else {
                    tvCreatedAt.setText("-");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // ignore for now
            }
        };

        userRef.addValueEventListener(userListener);

        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
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

        if (navDashboard != null) navDashboard.setOnClickListener(v -> startActivity(new android.content.Intent(ProfileActivity.this, DashboardActivity.class)));
        if (navExpenses != null) navExpenses.setOnClickListener(v -> startActivity(new android.content.Intent(ProfileActivity.this, ExpensesActivity.class)));
        if (navAdd != null) navAdd.setOnClickListener(v -> startActivity(new android.content.Intent(ProfileActivity.this, AddExpenseActivity.class)));
        if (navReports != null) navReports.setOnClickListener(v -> startActivity(new android.content.Intent(ProfileActivity.this, ReportsActivity.class)));
        if (navExport != null) navExport.setOnClickListener(v -> startActivity(new android.content.Intent(ProfileActivity.this, ExportActivity.class)));
    }

    private void setupProfileMenu() {
        if (imgProfile == null) imgProfile = findViewById(R.id.imgProfile);
        if (imgProfile == null) return;

        imgProfile.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(ProfileActivity.this, imgProfile);
            popupMenu.getMenuInflater().inflate(R.menu.menu_profile, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_view_profile) {
                    // already in profile, ignore or refresh
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
        android.content.Intent intent = new android.content.Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userRef != null && userListener != null) userRef.removeEventListener(userListener);
    }

    private void showEditProfileDialog() {
        // inflate card-based dialog layout
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Edit Profile");
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        final EditText etName = v.findViewById(R.id.etProfileName);
        etName.setText(tvName.getText());
        etName.setSelectAllOnFocus(true);
        b.setView(v);

        b.setPositiveButton("Save", (dialog, which) -> {
            String newName = etName.getText() == null ? "" : etName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(ProfileActivity.this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // update Realtime DB under users/{uid}/profile/name
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference profileNameRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("profile").child("name");
            profileNameRef.setValue(newName).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // also update auth displayName
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        UserProfileChangeRequest req = new UserProfileChangeRequest.Builder().setDisplayName(newName).build();
                        user.updateProfile(req).addOnCompleteListener(t2 -> {
                            if (t2.isSuccessful()) {
                                Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                                tvName.setText(newName);
                            } else {
                                Toast.makeText(ProfileActivity.this, "Updated DB but failed to update auth profile", Toast.LENGTH_SHORT).show();
                                tvName.setText(newName);
                            }
                        });
                    } else {
                        tvName.setText(newName);
                        Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(ProfileActivity.this, "Failed to update profile", Toast.LENGTH_SHORT).show();
                }
            });

        });

        b.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        b.show();
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Change Password");

        View v = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        final EditText etNew = v.findViewById(R.id.etNewPassword);
        final EditText etConfirm = v.findViewById(R.id.etConfirmPassword);

        b.setView(v);
        b.setPositiveButton("Change", (dialog, which) -> {
            String p1 = etNew.getText() == null ? "" : etNew.getText().toString();
            String p2 = etConfirm.getText() == null ? "" : etConfirm.getText().toString();
            if (p1.length() < 6) {
                Toast.makeText(ProfileActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!p1.equals(p2)) {
                Toast.makeText(ProfileActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return;

            user.updatePassword(p1).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // update password hash and salt in Realtime DB under users/{uid}/auth
                    String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    String salt = com.ra.set.utils.PasswordUtils.generateSalt();
                    String hash = com.ra.set.utils.PasswordUtils.hashPassword(p1, salt);
                    DatabaseReference authRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("auth");
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("salt", salt);
                    map.put("passwordHash", hash);
                    authRef.updateChildren(map).addOnCompleteListener(t2 -> {
                        if (t2.isSuccessful()) {
                            Toast.makeText(ProfileActivity.this, "Password changed successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ProfileActivity.this, "Password changed but failed to update DB", Toast.LENGTH_LONG).show();
                        }
                    });

                } else {
                    // if requires recent login, fallback to send password reset email
                    if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                        String email = user.getEmail();
                        if (email != null) {
                            FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(t -> {
                                if (t.isSuccessful()) {
                                    Toast.makeText(ProfileActivity.this, "Password change requires recent login. Reset email sent.", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(ProfileActivity.this, "Failed to send reset email", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } else {
                        Toast.makeText(ProfileActivity.this, "Failed to change password: " + (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                    }
                }
            });

        });

        b.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        b.show();
    }
}
