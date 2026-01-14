package com.ra.set.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import android.content.DialogInterface;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ra.set.databinding.ActivityLoginBinding;
import com.ra.set.utils.PasswordUtils;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private FirebaseFunctions functions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        functions = FirebaseFunctions.getInstance();

        // if already signed in -> go dashboard
        if (auth.getCurrentUser() != null) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        // show message if redirected from registration
        boolean registered = getIntent().getBooleanExtra("registered", false);
        if (registered) {
            Toast.makeText(this, "Registered successfully. Please log in.", Toast.LENGTH_LONG).show();
        }

        binding.btnLogin.setOnClickListener(v -> loginUser());
        binding.tvSignup.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        // forgot password
        binding.getRoot().findViewById(com.ra.set.R.id.tvForgotPassword).setOnClickListener(v -> showForgotDialog());
    }

    private void loginUser() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            binding.etEmail.setError("Email required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            binding.etPassword.setError("Password required");
            return;
        }

        binding.btnLogin.setEnabled(false);
        binding.btnLogin.setText("Logging in...");

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.btnLogin.setEnabled(true);
                    binding.btnLogin.setText("Login");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showForgotDialog() {
        // Inflate the custom dialog layout
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(com.ra.set.R.layout.dialog_reset_password_full, null);
        EditText etEmail = view.findViewById(com.ra.set.R.id.etResetEmail);
        EditText etNew = view.findViewById(com.ra.set.R.id.etNewPassword);
        EditText etConfirm = view.findViewById(com.ra.set.R.id.etConfirmPassword);
        // Note: dialog_change_password.xml uses builder buttons (no buttons inside the layout). We follow the same pattern.

        // Prefill email if already entered on login screen
        if (!TextUtils.isEmpty(binding.etEmail.getText())) {
            etEmail.setText(binding.etEmail.getText().toString().trim());
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setView(view);
        b.setPositiveButton("Reset", (dialogInterface, which) -> {
            String email = etEmail.getText() == null ? "" : etEmail.getText().toString().trim();
            String newPass = etNew.getText() == null ? "" : etNew.getText().toString();
            String confirm = etConfirm.getText() == null ? "" : etConfirm.getText().toString();

            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(newPass) || newPass.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPass.equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            // First, request a server-generated reset token to be emailed to the user
            Map<String, Object> data = new HashMap<>();
            data.put("email", email);

            functions.getHttpsCallable("generateReset").call(data).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, "Failed to request server reset token. Falling back to email reset. " + (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                    // fallback: only send password reset email
                    performDbUpdateAndSendEmail(email, (DialogInterface) dialogInterface);
                    return;
                }

                // Server reported success - prompt user to enter the token they received
                promptForTokenAndConfirm(email, newPass, dialogInterface);
            });
        });

        b.setNegativeButton("Cancel", (dialogInterface, which) -> dialogInterface.dismiss());

        AlertDialog dialog = b.create();
        dialog.show();
    }

    private void promptForTokenAndConfirm(String email, String newPass, DialogInterface parentDialog) {
        // Simple prompt dialog to enter token
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("Enter reset token");
        final EditText etToken = new EditText(this);
        etToken.setHint("Reset token");
        b.setView(etToken);
        b.setPositiveButton("Confirm", (d, which) -> {
            String token = etToken.getText() == null ? "" : etToken.getText().toString().trim();
            if (token.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Token is required", Toast.LENGTH_SHORT).show();
                return;
            }
            // call confirmReset
            Map<String, Object> payload = new HashMap<>();
            payload.put("token", token);
            payload.put("newPassword", newPass);
            functions.getHttpsCallable("confirmReset").call(payload).addOnCompleteListener(t -> {
                if (t.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, "Password for " + email + " has been reset successfully. You can now log in with the new password.", Toast.LENGTH_LONG).show();
                    if (parentDialog != null) parentDialog.dismiss();
                } else {
                    Toast.makeText(LoginActivity.this, "Failed to confirm reset: " + (t.getException() != null ? t.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                }
            });
        });
        b.setNegativeButton("Cancel", (d, which) -> d.dismiss());
        b.show();
    }

    private void performDbUpdateAndSendEmail(String email, DialogInterface parentDialog) {
        // Fallback: only send the Firebase Auth password reset email and do NOT modify the app-side password hash
        // Modifying the DB without updating Firebase Auth causes the app to still accept the old password.
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(resetTask -> {
            if (resetTask.isSuccessful()) {
                Toast.makeText(LoginActivity.this, "A password reset email has been sent to " + email + ". Please use the link in the email to reset your password.", Toast.LENGTH_LONG).show();
                if (parentDialog != null) parentDialog.dismiss();
            } else {
                String err = resetTask.getException() != null ? resetTask.getException().getMessage() : "";
                Toast.makeText(LoginActivity.this, "Failed to send password reset email: " + err, Toast.LENGTH_LONG).show();
            }
        });
    }
}
