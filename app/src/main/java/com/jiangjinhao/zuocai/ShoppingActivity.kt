package com.jiangjinhao.zuocai

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.jiangjinhao.zuocai.data.RecipeDb
import com.jiangjinhao.zuocai.data.ShoppingList
import com.jiangjinhao.zuocai.databinding.ActivityShoppingBinding
import com.jiangjinhao.zuocai.databinding.ItemShoppingBinding

/**
 * 买菜清单：从菜谱挑几道，食材合并成一张可勾选的清单。
 *
 * 条目操作：点行勾/取消（划线），长按删除单条；底部清空已勾选 / 清空全部。
 */
class ShoppingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShoppingBinding
    private var items = mutableListOf<ShoppingList.Item>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShoppingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.addButton.setOnClickListener { pickRecipes() }
        binding.clearChecked.setOnClickListener {
            val n = items.count { it.checked }
            if (n == 0) return@setOnClickListener
            items.removeAll { it.checked }
            saveAndRender()
            toast(getString(R.string.shopping_cleared))
        }
        binding.clearAll.setOnClickListener {
            if (items.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.shopping_clear_all) + "？")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    items.clear()
                    saveAndRender()
                    toast(getString(R.string.shopping_cleared))
                }
                .show()
        }

        items = ShoppingList.load(this)
        render()
    }

    private fun render() {
        binding.listContainer.removeAllViews()
        val empty = items.isEmpty()
        binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        binding.clearChecked.visibility = if (items.any { it.checked }) View.VISIBLE else View.GONE
        binding.clearAll.visibility = if (empty) View.GONE else View.VISIBLE

        items.forEachIndexed { index, item ->
            val row = ItemShoppingBinding.inflate(layoutInflater, binding.listContainer, false)
            row.itemCheck.setOnCheckedChangeListener(null)
            row.itemCheck.isChecked = item.checked
            row.itemName.text = item.name
            row.itemAmount.text = item.amount
            row.itemAmount.visibility = if (item.amount.isBlank()) View.GONE else View.VISIBLE
            strike(row.itemName, item.checked)

            row.root.setOnClickListener { v ->
                item.checked = !item.checked
                row.itemCheck.isChecked = item.checked
                strike(row.itemName, item.checked)
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(60)
                    .withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                    .start()
                binding.clearChecked.visibility =
                    if (items.any { it.checked }) View.VISIBLE else View.GONE
                ShoppingList.save(this, items)
            }
            row.root.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.shopping_delete_item, item.name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.dialog_delete) { _, _ ->
                        items.removeAt(index)
                        saveAndRender()
                        toast(getString(R.string.shopping_deleted))
                    }
                    .show()
                true
            }
            binding.listContainer.addView(row.root)
        }
    }

    private fun strike(tv: TextView, on: Boolean) {
        tv.paintFlags = if (on) {
            tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        tv.alpha = if (on) 0.45f else 1f
    }

    private fun saveAndRender() {
        ShoppingList.save(this, items)
        render()
    }

    /** 多选菜谱，把食材并进清单 */
    private fun pickRecipes() {
        val recipes = RecipeDb.get(this).list()
        if (recipes.isEmpty()) {
            toast(getString(R.string.export_empty))
            return
        }
        val names = recipes.map { it.name }.toTypedArray()
        val checked = BooleanArray(names.size)

        AlertDialog.Builder(this)
            .setTitle(R.string.shopping_pick_title)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.shopping_add) { _, _ ->
                val picked = recipes.filterIndexed { i, _ -> checked[i] }
                if (picked.isEmpty()) return@setPositiveButton
                val n = ShoppingList.addRecipes(this, picked)
                items = ShoppingList.load(this)
                render()
                toast(getString(R.string.shopping_added_fmt, n))
            }
            .show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
