package com.example.giuaki

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ProductAdapter(
    private val context: Context,
    private val productList: List<Product>,
    private var isAdmin: Boolean = true,
    private val onEditClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgSp: ImageView = itemView.findViewById(R.id.imgSp)
        val tvTenSp: TextView = itemView.findViewById(R.id.tvTenSp)
        val tvGiaSp: TextView = itemView.findViewById(R.id.tvGiaSp)
        val tvLoaiSp: TextView = itemView.findViewById(R.id.tvLoaiSp)
        val layoutAdminButtons: LinearLayout = itemView.findViewById(R.id.layoutAdminButtons)
        val btnSua: ImageButton = itemView.findViewById(R.id.btnSua)
        val btnXoa: ImageButton = itemView.findViewById(R.id.btnXoa)
    }

    fun setAdmin(admin: Boolean) {
        this.isAdmin = admin
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]
        holder.tvTenSp.text = "Tên sp: ${product.tenSp}"
        val giaFormatted = if (product.giaSp % 1.0 == 0.0) product.giaSp.toLong().toString() else product.giaSp.toString()
        holder.tvGiaSp.text = "Giá sp: $giaFormatted"
        holder.tvLoaiSp.text = "Loại sp: ${product.loaiSp}"

        if (product.imageUrl.isNotEmpty()) {
            Glide.with(context)
                .load(product.imageUrl)
                .centerCrop()
                .into(holder.imgSp)
        } else {
            holder.imgSp.setImageResource(R.drawable.ic_folder)
        }

        if (isAdmin) {
            holder.layoutAdminButtons.visibility = View.VISIBLE
            holder.btnSua.setOnClickListener { onEditClick(product) }
            holder.btnXoa.setOnClickListener { onDeleteClick(product) }
        } else {
            holder.layoutAdminButtons.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = productList.size
}