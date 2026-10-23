package com.example.giuaki

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private enum class RetryMode { COMPLETE_REGISTRATION, REREAD_ROLE }

    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var btnRetry: Button
    private lateinit var tvStatus: TextView

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    /** UID of an account created by registration in THIS session whose profile write failed. */
    private var pendingRegistrationUid: String? = null
    private var retryMode = RetryMode.REREAD_ROLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        btnRetry = findViewById(R.id.btnRetry)
        tvStatus = findViewById(R.id.tvStatus)

        btnLogin.setOnClickListener { loginUser() }
        btnRegister.setOnClickListener { registerUser() }
        btnRetry.setOnClickListener { onRetryClicked() }

        // Session restore: always re-read the role from Firestore.
        val current = auth.currentUser
        if (current != null) {
            setBusy(true)
            fetchRoleAndNavigate(current.uid)
        }
    }

    private fun setBusy(busy: Boolean) {
        btnLogin.isEnabled = !busy
        btnRegister.isEnabled = !busy
        btnRetry.isEnabled = !busy
    }

    private fun clearStatus() {
        tvStatus.visibility = View.GONE
        btnRetry.visibility = View.GONE
    }

    private fun showStatus(message: String, mode: RetryMode?) {
        tvStatus.text = message
        tvStatus.visibility = View.VISIBLE
        if (mode != null) {
            retryMode = mode
            btnRetry.visibility = View.VISIBLE
        } else {
            btnRetry.visibility = View.GONE
        }
        setBusy(false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ---------------------------------------------------------------- Login

    private fun loginUser() {
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập Email và Mật khẩu", Toast.LENGTH_SHORT).show()
            return
        }

        clearStatus()
        pendingRegistrationUid = null // normal login can never complete/create a profile
        setBusy(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Read exactly users/{FirebaseAuth.currentUser.uid}
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    setBusy(false)
                    Toast.makeText(this, "Không lấy được UID người dùng.", Toast.LENGTH_LONG).show()
                } else {
                    fetchRoleAndNavigate(uid)
                }
            }
            .addOnFailureListener { e ->
                setBusy(false)
                Toast.makeText(this, FirebaseErrors.auth(e), Toast.LENGTH_LONG).show()
            }
    }

    /** Read-only: never writes to Firestore. */
    private fun fetchRoleAndNavigate(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    auth.signOut()
                    showStatus(
                        "Tài khoản chưa có hồ sơ phân quyền (users/{uid}). " +
                            "Vui lòng yêu cầu quản trị viên cấu hình hồ sơ cho tài khoản này.",
                        null
                    )
                    return@addOnSuccessListener
                }
                when (doc.getString("role")) {
                    "admin" -> navigateToMain(true)
                    "user" -> navigateToMain(false)
                    else -> {
                        auth.signOut()
                        showStatus(
                            "Hồ sơ phân quyền thiếu role hoặc role không hợp lệ. " +
                                "Vui lòng yêu cầu quản trị viên sửa users/{uid}.role.",
                            null
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                // Cannot verify: do not guess the role and do not sign out; offer a read-only retry.
                showStatus(
                    "Không đọc được quyền người dùng. " + FirebaseErrors.firestore(e),
                    RetryMode.REREAD_ROLE
                )
            }
    }

    // ------------------------------------------------------------- Register

    private fun registerUser() {
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString()

        if (email.isEmpty() || password.length < 6) {
            Toast.makeText(this, "Email và mật khẩu từ 6 ký tự trở lên", Toast.LENGTH_SHORT).show()
            return
        }

        clearStatus()
        pendingRegistrationUid = null
        setBusy(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    setBusy(false)
                    Toast.makeText(this, "Không lấy được UID người dùng.", Toast.LENGTH_LONG).show()
                } else {
                    createRegistrationProfile(user.uid, user.email ?: email)
                }
            }
            .addOnFailureListener { e ->
                setBusy(false)
                Toast.makeText(this, FirebaseErrors.auth(e), Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Only used for a brand-new registration (or retrying that same failed registration).
     * Transaction: write role="user" only if the document does not exist; never overwrite.
     */
    private fun createRegistrationProfile(uid: String, email: String) {
        val ref = db.collection("users").document(uid)
        val data = hashMapOf<String, Any>(
            "email" to email,
            "role" to "user",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.runTransaction { tr ->
            if (!tr.get(ref).exists()) {
                tr.set(ref, data)
            }
            null
        }
            .addOnSuccessListener {
                pendingRegistrationUid = null
                clearStatus()
                Toast.makeText(this, "Đăng ký hoàn tất!", Toast.LENGTH_SHORT).show()
                fetchRoleAndNavigate(uid)
            }
            .addOnFailureListener { e ->
                pendingRegistrationUid = uid
                showStatus(
                    "Tài khoản đã tạo nhưng lưu hồ sơ users/{uid} thất bại. " +
                        FirebaseErrors.firestore(e) + " Nhấn THỬ LẠI (không cần đăng ký lại).",
                    RetryMode.COMPLETE_REGISTRATION
                )
            }
    }

    // ---------------------------------------------------------------- Retry

    private fun onRetryClicked() {
        val user = auth.currentUser
        if (user == null) {
            clearStatus()
            Toast.makeText(this, "Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.", Toast.LENGTH_LONG).show()
            return
        }
        when (retryMode) {
            RetryMode.COMPLETE_REGISTRATION -> {
                // Only allowed for the account whose registration just failed.
                if (pendingRegistrationUid != user.uid) {
                    clearStatus()
                    Toast.makeText(
                        this,
                        "Không có đăng ký nào đang chờ hoàn tất cho tài khoản này.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                setBusy(true)
                createRegistrationProfile(user.uid, user.email ?: "")
            }
            RetryMode.REREAD_ROLE -> {
                setBusy(true)
                fetchRoleAndNavigate(user.uid)
            }
        }
    }

    private fun navigateToMain(isAdmin: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("IS_ADMIN", isAdmin)
        startActivity(intent)
        finish()
    }
}