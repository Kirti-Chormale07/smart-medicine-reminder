package com.example.mediremind.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mediremind.R
import com.example.mediremind.data.Status
import com.example.mediremind.util.ImageUtil
import com.example.mediremind.util.TimeFmt

class DoseAdapter(private val onClick: (Row) -> Unit) :
    RecyclerView.Adapter<DoseAdapter.VH>() {

    data class Row(
        val medId: Long,
        val name: String,
        val dose: String,
        val photoPath: String?,
        val stock: Int,
        val at: Long,
        val status: Int,
        val relHint: String?
    )

    private val items = ArrayList<Row>()

    fun submit(rows: List<Row>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgMed)
        val name: TextView = view.findViewById(R.id.txtName)
        val dose: TextView = view.findViewById(R.id.txtDose)
        val stock: TextView = view.findViewById(R.id.txtStock)
        val time: TextView = view.findViewById(R.id.txtTime)
        val status: TextView = view.findViewById(R.id.txtStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dose, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val ctx = holder.itemView.context

        holder.name.text = row.name
        val doseText = if (row.relHint.isNullOrBlank()) row.dose
        else "${row.dose} • ${row.relHint}"
        holder.dose.text = doseText
        holder.time.text = TimeFmt.time(ctx, row.at)

        val photo = ImageUtil.decodeScaled(row.photoPath, 200)
        if (photo != null) {
            holder.img.setImageBitmap(photo)
        } else {
            holder.img.setImageResource(R.drawable.ic_pill_placeholder)
        }

        if (row.stock in 1..5) {
            holder.stock.visibility = View.VISIBLE
            holder.stock.text = ctx.getString(R.string.low_stock, row.stock)
        } else if (row.stock > 5) {
            holder.stock.visibility = View.VISIBLE
            holder.stock.text = ctx.getString(R.string.stock_left, row.stock)
        } else {
            holder.stock.visibility = View.GONE
        }

        when (row.status) {
            Status.TAKEN -> {
                holder.status.setText(R.string.status_taken)
                holder.status.setBackgroundResource(R.drawable.bg_chip_taken)
                holder.status.setTextColor(
                    androidx.core.content.ContextCompat.getColor(ctx, R.color.taken_green)
                )
            }
            Status.MISSED -> {
                holder.status.setText(R.string.status_missed)
                holder.status.setBackgroundResource(R.drawable.bg_chip_missed)
                holder.status.setTextColor(
                    androidx.core.content.ContextCompat.getColor(ctx, R.color.missed_red)
                )
            }
            Status.SNOOZED -> {
                holder.status.setText(R.string.status_snoozed)
                holder.status.setBackgroundResource(R.drawable.bg_chip_pending)
                holder.status.setTextColor(
                    androidx.core.content.ContextCompat.getColor(ctx, R.color.pending_amber)
                )
            }
            else -> {
                holder.status.setText(R.string.status_pending)
                holder.status.setBackgroundResource(R.drawable.bg_chip_pending)
                holder.status.setTextColor(
                    androidx.core.content.ContextCompat.getColor(ctx, R.color.pending_amber)
                )
            }
        }

        holder.itemView.setOnClickListener { onClick(row) }
    }
}
