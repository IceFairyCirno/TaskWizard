package com.example.todolist

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.example.todolist.databinding.ActivityMainBinding
import com.google.android.material.datepicker.MaterialDatePicker
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.todolist.databinding.DialogLayoutBinding
import com.example.todolist.Debugger
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.example.todolist.databinding.ItemLayoutBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dialogBinding: DialogLayoutBinding
    private lateinit var itemBinding: ItemLayoutBinding
    private lateinit var adapter: ItemAdapter

    private lateinit var db: AppDatabase
    private lateinit var eventDao: EventDao

    private val TAG = "To Do List"

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
        isGranted: Boolean ->
        if (isGranted) { Log.i(TAG, "Permission Granted") }
        else { Log.i(TAG, "Permission Denied") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        dialogBinding = DialogLayoutBinding.inflate(layoutInflater)
        itemBinding = ItemLayoutBinding.inflate(layoutInflater)

        setContentView(binding.root)
        val debugger = Debugger()

        // Init Database
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "app_database"
        ).build()
        eventDao = db.eventDao()

        // Check Permission for Notifications
        checkNotificationPermission()
        createNotificationChannel()

        // Setup the adapter
        adapter = ItemAdapter(emptyList(), onButtonClick = { event ->
            lifecycleScope.launch {
                Log.i(TAG, "Deleting Event: ${event.title}, ${event.date}, ${event.time}")
                eventDao.delete(event)
                val eventsFromDb = eventDao.getAllEvents()
                runOnUiThread { adapter.updateItems(eventsFromDb)}
                Log.i(TAG, "Event Deleted")
            }
        })
        binding.List.layoutManager = LinearLayoutManager(this)
        binding.List.adapter = adapter


        // Get All Events from the Database then display on screen
        lifecycleScope.launch {
            //eventDao.clearAllEvents()
            val eventsFromDb = eventDao.getAllEvents()
            runOnUiThread { adapter.updateItems(eventsFromDb) }
        }

        binding.AddNewItemButton.setOnClickListener {
            showInputDialog{title, date, time, notification->
                Log.i("Added TODO Item", "Title: $title, Date: $date, Time: $time")
                lifecycleScope.launch {
                    val requestCode = eventDao.insert(EventEntity(0, title, date, time))
                    scheduleNotification(requestCode, date, time, title, notification)
                    val updatedList = eventDao.getAllEvents()
                    runOnUiThread { adapter.updateItems(updatedList) }
                    displayAllEvents(eventDao)
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "Resuming... Updating the list")
        lifecycleScope.launch {
            val eventsFromDb = eventDao.getAllEvents()
            runOnUiThread { adapter.updateItems(eventsFromDb) }
        }
    }

    private fun createNotificationChannel() {
        val channelId = "1001"
        val channelName = "To Do List"
        val channelDescription = "To Do List Reminder"
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(this, "Please enable notifications for updates", Toast.LENGTH_LONG).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            Toast.makeText(this, "Notifications allowed (pre-Android 13)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInputDialog(onResult: (title: String, date: String, time: String, notification: Boolean) -> Unit){

        val dialogBinding = DialogLayoutBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        var selectedDate = java.time.LocalDate.now().toString()
        var selectedTime = "00:00"
        var notification = false

        dialogBinding.NotificationButtonsContainer.visibility = View.GONE

        dialogBinding.btnConfirm.setOnClickListener {
            val selectedTitle = dialogBinding.etTitle.text.toString().trim()
            if (selectedTitle != "" && selectedTitle != "Enter Title"){
                onResult(selectedTitle, selectedDate, selectedTime, notification)
                dialog.dismiss()
            } else{
                Toast.makeText(this, "Must enter a title", Toast.LENGTH_SHORT).show()
            }
        }
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        val calendar = Calendar.getInstance()

        dialogBinding.etDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker().setSelection(MaterialDatePicker.todayInUtcMilliseconds()).build()
            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(selection))
                dialogBinding.etDate.text = selectedDate
            }
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }
        dialogBinding.etTime.setOnClickListener {
            val timePickerDialog = TimePickerDialog(
                this,
                { _, selectedHour, selectedMinute ->
                    selectedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
                    dialogBinding.etTime.text = selectedTime
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            )
            timePickerDialog.show()
        }

        dialogBinding.EnableNotificationCheckBox.setOnClickListener {
            dialogBinding.NotificationButtonsContainer.visibility = if (dialogBinding.EnableNotificationCheckBox.isChecked) View.VISIBLE else View.GONE
            if (dialogBinding.EnableNotificationCheckBox.isChecked){
                notification = true
            } else{
                notification = false
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun scheduleNotification(requestCode: Long, selectDate: String, selectTime: String, selectedTitle: String, notification: Boolean) {
        val datetime = "$selectDate $selectTime"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()

        try {
            calendar.time = sdf.parse(datetime) ?: return

            if (calendar.timeInMillis < System.currentTimeMillis()) {
                Log.w("ScheduleNotification", "Attempted to schedule notification in the past: $datetime")
                return
            }

            val intent = Intent(this, NotificationReceiver::class.java).apply {
                putExtra("notificationTitle", "Reminder")
                putExtra("notificationMessage", "It's time to $selectedTitle, get it done")
                putExtra("requestCode", requestCode.toInt())
                putExtra("notificationPreference", notification)
            }
            val pendingIntent = PendingIntent.getBroadcast(this, requestCode.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

            val alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

            Log.i("ScheduleNotification", "Notification scheduled for $datetime")
        } catch (e: Exception) {
            Log.e("ScheduleNotification", "Failed to schedule notification", e)
        }
    }

    private fun displayAllEvents(eventDao: EventDao) {
        lifecycleScope.launch {
            val events = eventDao.getAllEvents()
            if (events.isEmpty()) {
                Log.d("EventLog", "No events found in the database.")
            } else {
                events.forEach { event ->
                    Log.d("EventLog", "Event(requestCode=${event.requestCode}, title=${event.title}, date=${event.date}, time=${event.time})")
                }
            }
        }
    }
}