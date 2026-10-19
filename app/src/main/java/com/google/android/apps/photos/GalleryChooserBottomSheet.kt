package com.google.android.apps.photos

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.apps.photos.databinding.DialogGalleryChooserBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * BottomSheet dialog presenting the user with all installed gallery applications
 * to choose from when previewing a photo or video taken with Pixel Camera.
 */
class GalleryChooserBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogGalleryChooserBinding? = null
    private val binding get() = _binding!!

    private var mimeType: String? = null
    private var onSelectedListener: ((gallery: GalleryAppInfo, rememberChoice: Boolean) -> Unit)? = null
    private var onDismissListener: (() -> Unit)? = null
    private var actionTaken = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mimeType = arguments?.getString(ARG_MIME_TYPE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogGalleryChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val isVideo = mimeType?.startsWith("video/") == true
        binding.dialogTitle.setText(
            if (isVideo) R.string.dialog_choose_gallery_title_video
            else R.string.dialog_choose_gallery_title
        )

        val galleries = GalleryManager.discoverGalleries(context, mimeType)
        val currentDefault = GalleryManager.getDefaultPackage(context)

        if (galleries.isEmpty()) {
            binding.dialogSubtitle.visibility = View.VISIBLE
            binding.dialogSubtitle.setText(R.string.no_galleries_message)
            binding.checkboxAlways.visibility = View.GONE
        } else {
            binding.dialogSubtitle.visibility = View.GONE
            binding.checkboxAlways.visibility = View.VISIBLE
        }

        val adapter = GalleryAdapter(galleries, currentDefault) { selectedGallery ->
            actionTaken = true
            val rememberChoice = binding.checkboxAlways.isChecked
            onSelectedListener?.invoke(selectedGallery, rememberChoice)
            dismiss()
        }

        binding.galleryRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.galleryRecyclerView.adapter = adapter

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    fun setOnGallerySelectedListener(listener: (gallery: GalleryAppInfo, rememberChoice: Boolean) -> Unit) {
        onSelectedListener = listener
    }

    fun setOnDismissListener(listener: () -> Unit) {
        onDismissListener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!actionTaken) {
            onDismissListener?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "GalleryChooserBottomSheet"
        private const val ARG_MIME_TYPE = "mime_type"

        fun newInstance(mimeType: String?): GalleryChooserBottomSheet {
            return GalleryChooserBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MIME_TYPE, mimeType)
                }
            }
        }
    }
}
