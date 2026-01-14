package com.ra.set.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ra.set.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import com.ra.set.utils.PasswordUtils;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference();

        findViewById(R.id.btnRegister).setOnClickListener(v -> registerUser());
        findViewById(R.id.tvLogin).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
    }

    private void registerUser() {
        String name = ((android.widget.EditText) findViewById(R.id.etName))
                .getText().toString().trim();
        String email = ((android.widget.EditText) findViewById(R.id.etEmail))
                .getText().toString().trim();
        String password = ((android.widget.EditText) findViewById(R.id.etPassword))
                .getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            toast("Name is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            toast("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            toast("Password must be at least 6 characters");
            return;
        }

        findViewById(R.id.btnRegister).setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result.getUser() == null) {
                        findViewById(R.id.btnRegister).setEnabled(true);
                        toast("Registration failed: no user returned");
                        return;
                    }
                    String uid = result.getUser().getUid();

                    Map<String, Object> user = new HashMap<>();
                    user.put("uid", uid);
                    user.put("name", name);
                    user.put("email", email);
                    user.put("createdAt", System.currentTimeMillis());

                    // generate salt and hash for password storage (server-side auth still handled by Firebase Auth)
                    String salt = PasswordUtils.generateSalt();
                    String pwdHash = PasswordUtils.hashPassword(password, salt);
                    Map<String, Object> authMap = new HashMap<>();
                    authMap.put("salt", salt);
                    authMap.put("passwordHash", pwdHash);
                    user.put("auth", authMap);

                    dbRef.child("users").child(uid)
                            .setValue(user)
                            .addOnSuccessListener(unused -> {
                                startActivity(new Intent(this, DashboardActivity.class));
                                finish();
                            })
                            .addOnFailureListener((Exception e) -> toast(e.getMessage()));
                })
                .addOnFailureListener((Exception e) -> {
                    findViewById(R.id.btnRegister).setEnabled(true);
                    toast(e.getMessage());
                });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
