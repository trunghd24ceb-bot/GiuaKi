package com.example.giuaki

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class AssetImageAdapter(
    private val context: Context,
    private val imageList: List<String>,
    private val onImageSelected: (String) -> Unit
) : RecyclerView.Adapter<AssetImageAdapter.AssetImageViewHolder>() {

    class AssetImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgThumbnail: ImageView = itemView.findViewById(R.id.imgThumbnail)
        val tvImageName: TextView = itemView.findViewById(R.id.tvImageName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetImageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_asset_image, parent, false)
        return AssetImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssetImageViewHolder, position: Int) {
        val fileName = imageList[position]
        holder.tvImageName.text = fileName

        // Load image directly from asset
        val assetPath = "file:///android_asset/anh/$fileName"
        Glide.with(context)
            .load(assetPath)
            .centerCrop()
            .placeholder(R.drawable.ic_folder)
            .into(holder.imgThumbnail)

        holder.itemView.setOnClickListener {
            onImageSelected(fileName)
        }
    }

    override fun getItemCount(): Int = imageList.size
}
