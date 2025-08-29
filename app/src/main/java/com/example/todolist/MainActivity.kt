package com.example.todolist

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
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
import androidx.core.graphics.toColorInt
import java.time.LocalDate
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dialogBinding: DialogLayoutBinding
    private lateinit var itemBinding: ItemLayoutBinding
    private lateinit var adapter: ItemAdapter

    private lateinit var db: AppDatabase
    private lateinit var eventDao: EventDao

    private val TAG = "MainActivity.kt"

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
        isGranted: Boolean ->
        if (isGranted) { Log.i(TAG, "Permission Granted") }
        else { Log.i(TAG, "Permission Denied") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.i(TAG, "Creating MainActivity")

        binding = ActivityMainBinding.inflate(layoutInflater)
        dialogBinding = DialogLayoutBinding.inflate(layoutInflater)
        itemBinding = ItemLayoutBinding.inflate(layoutInflater)

        setContentView(binding.root)
        val debugger = Debugger()

        // Init Database
        Log.i(TAG, "Initializing Database")
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "app_database"
        ).build()
        eventDao = db.eventDao()

        // Check Permission for Notifications
        Log.i(TAG, "Checking Permission")
        checkNotificationPermission()
        Log.i(TAG, "Creating Notification Channel")
        createNotificationChannel()

        // Setup the adapter
        adapter = ItemAdapter(emptyList(), onButtonClick = { event ->
            lifecycleScope.launch {
                Log.i(TAG, "Deleting Event: ${event.title}, ${event.date}, ${event.time}, ${event.type}")
                eventDao.delete(event)
                binding.AllTab.performClick()
                val eventsFromDb = eventDao.getAllEvents()
                runOnUiThread { adapter.updateItems(eventsFromDb)}
                Log.i(TAG, "Event Successfully Deleted")
            }
        })
        binding.List.layoutManager = LinearLayoutManager(this)
        binding.List.adapter = adapter

        val dates = listOf(
            LocalDate.now(),
            LocalDate.now().plusDays(1),
            LocalDate.now().plusDays(2)
        ).map { it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }

        // Get All Events from the Database then display on screen
        Log.i(TAG, "Reading Database and Displaying Events")
        lifecycleScope.launch {
            //eventDao.clearAllEvents()
            val eventsFromDb = eventDao.getTodayEvents(dates[0])
            runOnUiThread { adapter.updateItems(eventsFromDb) }
        }

        val tabs = listOf(binding.TodayTab, binding.AllTab, binding.WeeklyTab, binding.UpcomingTab)
        tabs.forEachIndexed { index, cardView ->
            cardView.setOnClickListener {
                tabs.forEach { it.strokeColor = Color.TRANSPARENT }
                cardView.strokeColor = "#a6ccde".toColorInt()
                val textView = cardView.getChildAt(1) as TextView
                binding.CurrentTab.text = textView.text

                lifecycleScope.launch {
                    val eventsFromDb = when (index) {
                        0 -> eventDao.getTodayEvents(dates[0])
                        1 -> eventDao.getAllEvents()
                        2 -> eventDao.getWeeklyEvents()
                        3 -> eventDao.getUpcomingEvents(dates[0], dates[1], dates[2])
                        else -> eventDao.getTodayEvents(dates[0])
                    }
                    runOnUiThread { adapter.updateItems(eventsFromDb) }
                }
            }
        }


        binding.AddNewItemButton.setOnClickListener {
            showInputDialog{title, date, time, notification, eventType->
                Log.i(TAG, "Attempting to Add Event: ${title}, ${date}, ${time}, $eventType")
                lifecycleScope.launch {
                    val event = EventEntity(0, title, date, time, eventType, notification, false)
                    val requestCode = eventDao.insert(event)
                    Log.i(TAG, "Added Event to Database: ${event.title}, ${event.date}, ${event.time}, ${event.type}")
                    if (event.notification == "constant"){
                        Log.i(TAG, "Event Notification Type: Constant Notify")
                        val nextHour = getNextHourFromCurrent()
                        scheduleNotification(requestCode, nextHour.first, nextHour.second, title, notification)
                    } else if (event.notification == "hour"){
                        Log.i(TAG, "Event Notification Type: Hour Before Notify")
                        val hourBefore = getPreviousHourDateTime(date, time, "hour")
                        scheduleNotification(requestCode, hourBefore.first, hourBefore.second, title, notification)
                    } else if (event.notification == "day") {
                        Log.i(TAG, "Event Notification Type: Day Before Notify")
                        val dayBefore = getPreviousHourDateTime(date, time, "day")
                        scheduleNotification(requestCode, dayBefore.first, dayBefore.second, title, notification)
                    } else{
                        Log.i(TAG, "Event Notification Type: Default/None Notify")
                        scheduleNotification(requestCode, date, time, title, notification)
                    }
                    Log.i(TAG, "Updating the Current Events On Screen")
                    binding.AllTab.performClick()
                    displayAllEvents(eventDao)
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "Resuming... Updating the list")
        lifecycleScope.launch {
            binding.AllTab.performClick()
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

    private fun showInputDialog(onResult: (title: String, date: String, time: String, notification: String, eventType: String) -> Unit){

        Log.i("InputDialog", "Opened Input Dialog")
        val dialogBinding = DialogLayoutBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        var selectedDate = java.time.LocalDate.now().toString()
        var selectedTime = SimpleDateFormat("hh:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + 3600000))
        var notification = "none"
        var selectedEventType = "personal"

        dialogBinding.NotificationButtonsContainer.visibility = View.GONE

        dialogBinding.btnConfirm.setOnClickListener {
            val selectedTitle = dialogBinding.etTitle.text.toString().trim()
            if (selectedTitle != "" && selectedTitle != "Enter Title"){
                Log.i("InputDialog", "Event Created: $selectedTitle, $selectedDate, $selectedTime, $selectedEventType, Notification Enabled? $notification")
                onResult(selectedTitle, selectedDate, selectedTime, notification, selectedEventType)
                Log.i("InputDialog", "Closing Input Dialog")
                Toast.makeText(this, "Event Noted", Toast.LENGTH_SHORT).show()
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
            if (dialogBinding.EnableNotificationCheckBox.isChecked){ notification = "default" }
            else{ notification = "none" }
        }

        dialogBinding.ConstantNotifyCheckbox.setOnClickListener {
            if (dialogBinding.ConstantNotifyCheckbox.isChecked){ notification = "constant" }
            else{ notification = "default" }
        }
        dialogBinding.HourBeforeNotifyCheckbox.setOnClickListener {
            if (dialogBinding.HourBeforeNotifyCheckbox.isChecked){ notification = "hour" }
            else{ notification = "default" }
        }
        dialogBinding.DayBeforeNotifyCheckbox.setOnClickListener {
            if (dialogBinding.DayBeforeNotifyCheckbox.isChecked){ notification = "day" }
            else{ notification = "default" }
        }

        val eventTypes = listOf(dialogBinding.HomeEventType,
                            dialogBinding.WorkEventType,
                            dialogBinding.FriendEventType,
                            dialogBinding.PersonalEventType,
                            dialogBinding.StudyEventType)
        val typeNames = listOf("home", "work", "friend", "personal", "study")
        eventTypes.forEachIndexed { index, cardView ->
            cardView.setOnClickListener {
                selectedEventType = typeNames[index]
                eventTypes.forEach { it.strokeColor = Color.TRANSPARENT }
                cardView.strokeColor = "#9D9D9D".toColorInt()
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun scheduleNotification(requestCode: Long, selectDate: String, selectTime: String, selectedTitle: String, notification: String) {
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
                putExtra("notification", notification)
            }
            val pendingIntent = PendingIntent.getBroadcast(this, requestCode.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

            val alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

            Log.i("ScheduleNotification", "Notification scheduled for $datetime")
        } catch (e: Exception) {
            Log.e("ScheduleNotification", "Failed to schedule notification", e)
        }
    }

    fun getNextHourFromCurrent(): Pair<String, String> {
        val calendar = Calendar.getInstance() // Gets current time (09:31 AM HKT, August 28, 2025)
        calendar.add(Calendar.HOUR_OF_DAY, 1) // Add 1 hour

        val dateFormatDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateFormatTime = SimpleDateFormat("HH:mm", Locale.getDefault())

        val nextDate = dateFormatDate.format(calendar.time)
        val nextTime = dateFormatTime.format(calendar.time)

        return Pair(nextDate, nextTime)
    }

    fun getPreviousHourDateTime(dateStr: String, timeStr: String, unit: String): Pair<String, String> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val dateTimeStr = "$dateStr $timeStr"
        val calendar = Calendar.getInstance().apply {
            time = dateFormat.parse(dateTimeStr) ?: throw IllegalArgumentException("Invalid date or time format")
        }

        when (unit.lowercase()) {
            "hour" -> calendar.add(Calendar.HOUR_OF_DAY, -1)
            "day" -> calendar.add(Calendar.DAY_OF_MONTH, -1)
            else -> throw IllegalArgumentException("Unit must be 'hour' or 'day'")
        }

        val previousDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val previousTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)

        return Pair(previousDate, previousTime)
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