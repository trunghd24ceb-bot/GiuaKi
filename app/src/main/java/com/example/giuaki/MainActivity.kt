package com.example.giuaki

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.io.File
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var imageUri: Uri? = null
    private var directImageUrl: String = ""
    private lateinit var edtTen: EditText
    private lateinit var edtLoai: EditText
    private lateinit var edtGia: EditText
    private lateinit var btnChonAnhBox: LinearLayout
    private lateinit var tvTenAnh: TextView
    private lateinit var btnThem: Button
    private lateinit var btnLogout: ImageButton
    private lateinit var layoutAdminForm: LinearLayout
    private lateinit var recyclerView: RecyclerView

    private var isAdmin = false

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var productsListener: ListenerRegistration? = null
    private val productList = mutableListOf<Product>()
    private lateinit var adapter: ProductAdapter
    private var isEditing = false
    private var editProductId = ""

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            directImageUrl = ""
            tvTenAnh.text = getFileName(uri)
            tvTenAnh.setTextColor(getColor(android.R.color.black))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No Firebase session -> back to login.
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Never default to admin; Firestore Rules also enforce this server-side.
        isAdmin = intent.getBooleanExtra("IS_ADMIN", false)

        edtTen = findViewById(R.id.edtTen)
        edtLoai = findViewById(R.id.edtLoai)
        edtGia = findViewById(R.id.edtGia)
        btnChonAnhBox = findViewById(R.id.btnChonAnhBox)
        tvTenAnh = findViewById(R.id.tvTenAnh)
        btnThem = findViewById(R.id.btnThem)
        btnLogout = findViewById(R.id.btnLogout)
        layoutAdminForm = findViewById(R.id.layoutAdminForm)
        recyclerView = findViewById(R.id.recyclerView)

        // Set role UI visibility
        if (!isAdmin) {
            layoutAdminForm.visibility = View.GONE
        }

        adapter = ProductAdapter(
            context = this,
            productList = productList,
            isAdmin = isAdmin,
            onEditClick = { p -> editProduct(p) },
            onDeleteClick = { p -> deleteProduct(p) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadProducts()

        btnChonAnhBox.setOnClickListener { showImagePickerDialog() }

        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnThem.setOnClickListener {
            val ten = edtTen.text.toString().trim()
            val loai = edtLoai.text.toString().trim()
            val gia = edtGia.text.toString().toDoubleOrNull() ?: 0.0

            if (ten.isEmpty() || loai.isEmpty()) {
                Toast.makeText(this, "Nhập đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (directImageUrl.isNotEmpty()) {
                saveToFirestore(ten, loai, gia, directImageUrl)
            } else if (imageUri != null) {
                Toast.makeText(this, "Đang tải ảnh...", Toast.LENGTH_SHORT).show()
                try {
                    MediaManager.get().upload(imageUri).unsigned("ecomer_preset").callback(object : UploadCallback {
                        override fun onStart(requestId: String?) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Bắt đầu tải ảnh...", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                        override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                            val url = resultData?.get("secure_url")?.toString() ?: ""
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Tải ảnh thành công!", Toast.LENGTH_SHORT).show()
                                saveToFirestore(ten, loai, gia, url)
                            }
                        }
                        override fun onError(requestId: String?, error: ErrorInfo?) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Lỗi Cloudinary: ${error?.description}", Toast.LENGTH_LONG).show()
                            }
                        }
                        override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                    }).dispatch()
                } catch (e: Exception) {
                    Toast.makeText(this, "Lỗi Cloudinary: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else if (isEditing) {
                saveToFirestore(ten, loai, gia, "")
            } else {
                Toast.makeText(this, "Vui lòng chọn ảnh hoặc dán link ảnh!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImagePickerDialog() {
        val assetImages = try {
            assets.list("anh")?.filter {
                it.endsWith(".png", true) || it.endsWith(".jpg", true) ||
                it.endsWith(".jpeg", true) || it.endsWith(".webp", true)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_select_asset_image, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val rvAssetImages: RecyclerView = dialogView.findViewById(R.id.rvAssetImages)
        val tvEmptyAssets: TextView = dialogView.findViewById(R.id.tvEmptyAssets)
        val btnPickFromDevice: Button = dialogView.findViewById(R.id.btnPickFromDevice)
        val btnEnterImageUrl: Button = dialogView.findViewById(R.id.btnEnterImageUrl)
        val btnCloseDialog: Button = dialogView.findViewById(R.id.btnCloseDialog)

        if (assetImages.isEmpty()) {
            tvEmptyAssets.visibility = View.VISIBLE
            rvAssetImages.visibility = View.GONE
        } else {
            tvEmptyAssets.visibility = View.GONE
            rvAssetImages.visibility = View.VISIBLE
            rvAssetImages.layoutManager = GridLayoutManager(this, 2)
            rvAssetImages.adapter = AssetImageAdapter(this, assetImages) { selectedFile ->
                selectAssetImage(selectedFile)
                dialog.dismiss()
            }
        }

        btnPickFromDevice.setOnClickListener {
            dialog.dismiss()
            pickImageLauncher.launch("image/*")
        }

        btnEnterImageUrl.setOnClickListener {
            dialog.dismiss()
            showEnterUrlDialog()
        }

        btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEnterUrlDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/anh-san-pham.jpg"
            setPadding(40, 30, 40, 30)
            if (directImageUrl.isNotEmpty()) {
                setText(directImageUrl)
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Dán link ảnh (URL)")
            .setMessage("Nhập hoặc dán trực tiếp đường link ảnh từ web:")
            .setView(input)
            .setPositiveButton("Xác nhận") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    directImageUrl = url
                    imageUri = null
                    tvTenAnh.text = url
                    tvTenAnh.setTextColor(getColor(android.R.color.black))
                    Toast.makeText(this, "Đã chọn link ảnh URL!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Link ảnh không được để trống!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun selectAssetImage(fileName: String) {
        try {
            val cacheFile = File(cacheDir, fileName)
            assets.open("anh/$fileName").use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            imageUri = Uri.fromFile(cacheFile)
            directImageUrl = ""
            tvTenAnh.text = fileName
            tvTenAnh.setTextColor(getColor(android.R.color.black))
            Toast.makeText(this, "Đã chọn: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "image.jpg"
        }
        var name = "image.jpg"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun saveToFirestore(ten: String, loai: String, gia: Double, url: String) {
        if (!isAdmin) {
            Toast.makeText(this, "Bạn không có quyền thêm/sửa sản phẩm.", Toast.LENGTH_LONG).show()
            btnThem.isEnabled = true
            return
        }
        val updating = isEditing && editProductId.isNotEmpty()
        val id = if (updating) editProductId else UUID.randomUUID().toString()
        val existingImg = productList.find { it.id == editProductId }?.let { if (it.image.isNotEmpty()) it.image else it.imageUrl } ?: ""
        val finalUrl = if (url.isNotEmpty()) url else existingImg

        val productMap = hashMapOf<String, Any>(
            "id" to id,
            "tenSp" to ten,
            "loaiSp" to loai,
            "giaSp" to gia,
            "image" to finalUrl
        )

        btnThem.isEnabled = false
        // The list is refreshed by the snapshot listener only after Firestore accepts the write.
        db.collection(COLLECTION).document(id).set(productMap, SetOptions.merge())
            .addOnSuccessListener {
                btnThem.isEnabled = true
                Toast.makeText(this, if (updating) "Cập nhật thành công!" else "Thêm thành công!", Toast.LENGTH_SHORT).show()
                resetForm()
            }
            .addOnFailureListener { e ->
                btnThem.isEnabled = true
                Toast.makeText(this, FirebaseErrors.firestore(e, if (updating) "cập nhật" else "thêm"), Toast.LENGTH_LONG).show()
            }
    }

    private fun resetForm() {
        isEditing = false
        editProductId = ""
        btnThem.text = "THÊM SẢN PHẨM"
        edtTen.text.clear()
        edtLoai.text.clear()
        edtGia.text.clear()
        tvTenAnh.text = "Chọn hình ảnh..."
        tvTenAnh.setTextColor(getColor(android.R.color.darker_gray))
        imageUri = null
        directImageUrl = ""
    }

    /** Realtime listener on the single "products" collection. */
    private fun loadProducts() {
        productsListener = db.collection(COLLECTION).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Toast.makeText(this, FirebaseErrors.firestore(e, "tải"), Toast.LENGTH_LONG).show()
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            productList.clear()
            for (doc in snapshot) {
                if (doc.contains("tenSp")) {
                    val p = doc.toObject(Product::class.java)
                    p.id = doc.id
                    if (p.image.isEmpty() && p.imageUrl.isNotEmpty()) {
                        p.image = p.imageUrl
                    } else if (p.imageUrl.isEmpty() && p.image.isNotEmpty()) {
                        p.imageUrl = p.image
                    }
                    productList.add(p)
                }
            }
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        productsListener?.remove()
        super.onDestroy()
    }

    private fun editProduct(product: Product) {
        edtTen.setText(product.tenSp)
        edtLoai.setText(product.loaiSp)
        edtGia.setText(if (product.giaSp % 1.0 == 0.0) product.giaSp.toLong().toString() else product.giaSp.toString())
        btnThem.text = "CẬP NHẬT"
        isEditing = true
        editProductId = product.id
        val img = if (product.image.isNotEmpty()) product.image else product.imageUrl
        if (img.isNotEmpty()) {
            directImageUrl = img
            imageUri = null
            tvTenAnh.text = img
            tvTenAnh.setTextColor(getColor(android.R.color.black))
        }
    }

    private fun deleteProduct(product: Product) {
        if (!isAdmin) {
            Toast.makeText(this, "Bạn không có quyền xóa sản phẩm.", Toast.LENGTH_LONG).show()
            return
        }
        if (product.id.isEmpty()) return
        db.collection(COLLECTION).document(product.id).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Đã xóa sản phẩm", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, FirebaseErrors.firestore(e, "xóa"), Toast.LENGTH_LONG).show()
            }
    }

    companion object {
        private const val COLLECTION = "products"
    }
}