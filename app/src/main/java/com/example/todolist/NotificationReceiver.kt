package com.example.todolist

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = "1001"
        val title = intent.getStringExtra("notificationTitle") ?: "TaskWizard Reminder"
        val message = intent.getStringExtra("notificationMessage") ?: "It's time for your task!"
        val requestCode = intent.getIntExtra("requestCode", -1)
        val notification= intent.getStringExtra("notification") ?: "none"
        var safeToDelete = true

        CoroutineScope(Dispatchers.IO).launch {
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database"
            ).build()
            val eventDao = db.eventDao()
            val event = eventDao.getEventByRequestCode(requestCode)

            if ((event != null) and (notification != "none")){
                Log.i("NotificationReceiver", "Showing notification: $title - $message")

                val notificationBuilder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_todolist)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)

                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(1001, notificationBuilder.build())

                if (notification == "constant"){
                    val nextHour = getNextHourFromCurrent()
                    if (event != null) {
                        if (compareDateTimes(nextHour, Pair(event.date, event.time)) <= 0){
                            safeToDelete = false
                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            val newIntent = Intent(context, NotificationReceiver::class.java).apply {
                                putExtra("notificationTitle", title)
                                putExtra("notificationMessage", message)
                                putExtra("requestCode", requestCode)
                                putExtra("notification", notification)
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                requestCode, // Same request code
                                newIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Update existing PendingIntent
                            )
                            val currentTime = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
                            val nextAlarmTime = currentTime.timeInMillis + (60* 60 * 1000) // 60*60*1000
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent
                            )
                            Log.i("NotificationReceiver", "New constant alarm set for next hour: ${nextHour.first}, ${nextHour.second}")
                        } else{
                            safeToDelete = true
                            Log.i("NotificationReceiver", "Constant Alarm Ends")
                        }
                    }
                }else if (notification == "hour"){
                    val now = getCurrentDateTime()
                    if (event != null) {
                        if (compareDateTimes(now, Pair(event.date, event.time)) <= 0){
                            safeToDelete = false
                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            val newIntent = Intent(context, NotificationReceiver::class.java).apply {
                                putExtra("notificationTitle", title)
                                putExtra("notificationMessage", message)
                                putExtra("requestCode", requestCode)
                                putExtra("notification", notification)
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                requestCode, // Same request code
                                newIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Update existing PendingIntent
                            )
                            val currentTime = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
                            val nextAlarmTime = currentTime.timeInMillis + (60 * 60 * 1000)
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent
                            )
                            Log.i("NotificationReceiver", "New hour before alarm set for next hour (final): ${event.date}, ${event.time}")
                        } else{
                            safeToDelete = true
                        }
                    }
                }else if (notification == "day"){
                    val now = getCurrentDateTime()
                    if (event != null) {
                        if (compareDateTimes(now, Pair(event.date, event.time)) <= 0){
                            safeToDelete = false
                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            val newIntent = Intent(context, NotificationReceiver::class.java).apply {
                                putExtra("notificationTitle", title)
                                putExtra("notificationMessage", message)
                                putExtra("requestCode", requestCode)
                                putExtra("notification", notification)
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                requestCode, // Same request code
                                newIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Update existing PendingIntent
                            )
                            val currentTime = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
                            val nextAlarmTime = currentTime.timeInMillis + (60 * 60 * 1000 * 24)
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent
                            )
                            Log.i("NotificationReceiver", "New day before alarm set for next day (final): ${event.date}, ${event.time}")
                        } else{
                            safeToDelete = true
                        }
                    }
                }

                // Delete Event Activity
                if (safeToDelete){
                    val eventToDelete = eventDao.getEventByRequestCode(requestCode)
                    if (eventToDelete != null) {
                        eventDao.delete(eventToDelete)
                        Log.i("NotificationReceiver", "Deleted event with requestCode: $requestCode")
                    } else {
                        Log.w("NotificationReceiver", "Event with requestCode $requestCode not found.")
                    }
                }

            } else if ((notification == "none") and (event != null)){
                Log.i("NotificationReceiver", "Event with notification disabled, not sending notification")
                val eventToDelete = eventDao.getEventByRequestCode(requestCode)
                if (eventToDelete != null) {
                    eventDao.delete(eventToDelete)
                    Log.i("NotificationReceiver", "Deleted event with requestCode: $requestCode")
                } else {
                    Log.w("NotificationReceiver", "Event with requestCode $requestCode not found.")
                }
            } else{
                Log.i("NotificationReceiver", "Event Not Found, Might Be Deleted")
                Log.i("NotificationReceiver", "Not Sending Notification")
            }
        }
    }

    private fun getNextHourFromCurrent(): Pair<String, String> {
        val calendar = Calendar.getInstance() // Gets current time (09:31 AM HKT, August 28, 2025)
        calendar.add(Calendar.HOUR_OF_DAY, 1) // Add 1 hour

        val dateFormatDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateFormatTime = SimpleDateFormat("HH:mm", Locale.getDefault())

        val nextDate = dateFormatDate.format(calendar.time)
        val nextTime = dateFormatTime.format(calendar.time)

        return Pair(nextDate, nextTime)
    }
    fun getCurrentDateTime(): Pair<String, String> {
        val calendar = Calendar.getInstance()
        val dateFormatDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateFormatTime = SimpleDateFormat("HH:mm", Locale.getDefault())

        val currentDate = dateFormatDate.format(calendar.time)
        val currentTime = dateFormatTime.format(calendar.time)

        return Pair(currentDate, currentTime)
    }
    fun compareDateTimes(dateTime1: Pair<String, String>, dateTime2: Pair<String, String>): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val dateTimeStr1 = "${dateTime1.first} ${dateTime1.second}"
        val dateTimeStr2 = "${dateTime2.first} ${dateTime2.second}"

        val calendar1 = Calendar.getInstance().apply {
            time = dateFormat.parse(dateTimeStr1) ?: throw IllegalArgumentException("Invalid date or time format for first datetime")
        }
        val calendar2 = Calendar.getInstance().apply {
            time = dateFormat.parse(dateTimeStr2) ?: throw IllegalArgumentException("Invalid date or time format for second datetime")
        }

        return calendar1.timeInMillis.compareTo(calendar2.timeInMillis)
    }
}
