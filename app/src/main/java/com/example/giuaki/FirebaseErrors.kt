package com.example.giuaki

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

object FirebaseErrors {

    private const val NETWORK = "Lỗi mạng: không kết nối được tới Firebase. Kiểm tra Internet và thử lại."
    private const val PERMISSION =
        "Lỗi quyền truy cập (permission denied): Firestore Rules từ chối thao tác này. " +
            "Kiểm tra Rules đã Publish và quyền (role) của tài khoản."

    /** Errors from FirebaseAuth sign-in / sign-up. */
    fun auth(e: Exception?): String = when (e) {
        is FirebaseNetworkException -> NETWORK
        is FirebaseAuthWeakPasswordException -> "Mật khẩu quá yếu (tối thiểu 6 ký tự)."
        is FirebaseAuthUserCollisionException -> "Email này đã được đăng ký. Vui lòng đăng nhập."
        is FirebaseAuthInvalidUserException -> "Tài khoản không tồn tại hoặc đã bị vô hiệu hóa."
        is FirebaseAuthInvalidCredentialsException -> "Email hoặc mật khẩu không chính xác (hoặc email sai định dạng)."
        is FirebaseFirestoreException -> firestore(e)
        else -> "Lỗi đăng nhập/đăng ký: ${e?.localizedMessage ?: "không xác định"}"
    }

    /** Errors from Firestore reads/writes. [action] e.g. "thêm", "cập nhật", "xóa", "tải". */
    fun firestore(e: Exception?, action: String = ""): String {
        val prefix = if (action.isNotEmpty()) "Lỗi $action sản phẩm: " else ""
        if (e is FirebaseFirestoreException) {
            return when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> PERMISSION
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> NETWORK
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại."
                else -> "$prefix${e.code}: ${e.localizedMessage}"
            }
        }
        if (e is FirebaseNetworkException) return NETWORK
        return "$prefix${e?.localizedMessage ?: "không xác định"}"
    }
}
