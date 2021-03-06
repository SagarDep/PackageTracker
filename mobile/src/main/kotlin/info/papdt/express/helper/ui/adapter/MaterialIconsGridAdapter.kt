package info.papdt.express.helper.ui.adapter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import info.papdt.express.helper.R
import info.papdt.express.helper.model.MaterialIcon
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import me.drakeet.multitype.ItemViewBinder
import me.drakeet.multitype.MultiTypeAdapter

class MaterialIconsGridAdapter : MultiTypeAdapter() {

    private var keyword: String? = null

    private var searchJob: Job? = null

    var callback: (String) -> Unit = {}

    init {
        register(MaterialIcon::class.java, IconItemBinder())
    }

    fun update(keyword: String? = null) {
        this.keyword = keyword
        searchJob?.cancel()
        searchJob = async(UI) {
            val result = async(CommonPool) { MaterialIcon.search(keyword) }.await()
            items = result.map { MaterialIcon(it) }
            notifyDataSetChanged()
        }
    }

    fun destroy() {
        searchJob?.cancel()
    }

    inner class IconItemBinder : ItemViewBinder<MaterialIcon, IconItemBinder.ViewHolder>() {

        override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder {
            return ViewHolder(inflater.inflate(R.layout.grid_item_material_icon, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, item: MaterialIcon) {
            holder.job?.cancel()
            holder.job = async(UI) {
                holder.iconView.setImageBitmap(item.toBitmapAsync(holder.iconSize).await())
            }
            holder.textView.text = item.code
        }

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            holder.job?.cancel()
            holder.iconView.setImageBitmap(null)
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            internal var job: Job? = null

            val iconView: ImageView = itemView.findViewById(R.id.icon_view)
            val textView: TextView = itemView.findViewById(R.id.text_view)

            val iconSize: Int = itemView.resources.getDimensionPixelSize(R.dimen.icon_size_medium)

            init {
                itemView.setOnClickListener {
                    callback(textView.text.toString())
                }
            }

        }

    }

}