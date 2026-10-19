package com.google.android.apps.photos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.apps.photos.databinding.ItemGalleryAppBinding

/**
 * RecyclerView Adapter for displaying discovered gallery applications in the chooser dialog.
 */
class GalleryAdapter(
    private val galleries: List<GalleryAppInfo>,
    private val currentDefaultPackage: String?,
    private val onGallerySelected: (GalleryAppInfo) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = ItemGalleryAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(galleries[position])
    }

    override fun getItemCount(): Int = galleries.size

    inner class GalleryViewHolder(
        private val binding: ItemGalleryAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gallery: GalleryAppInfo) {
            binding.appIcon.setImageDrawable(gallery.icon)
            binding.appName.text = gallery.appName

            val isDefault = gallery.packageName == currentDefaultPackage
            binding.defaultBadge.visibility = if (isDefault) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onGallerySelected(gallery)
            }
        }
    }
}
