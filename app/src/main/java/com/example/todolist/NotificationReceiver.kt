package com.example.todolist

import android.annotation.SuppressLint
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

class NotificationReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = "1001"
        val title = intent.getStringExtra("notificationTitle") ?: "TaskWizard Reminder"
        val message = intent.getStringExtra("notificationMessage") ?: "It's time for your task!"
        val requestCode = intent.getIntExtra("requestCode", -1)
        val notificationPreference = intent.getBooleanExtra("notificationPreference", false)

        CoroutineScope(Dispatchers.IO).launch {
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database"
            ).build()
            val eventDao = db.eventDao()
            val event = eventDao.getEventByRequestCode(requestCode)

            if ((event != null) and (notificationPreference)){
                Log.i("NotificationReceiver", "Showing notification: $title - $message")

                val notificationBuilder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_todolist)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)

                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(1001, notificationBuilder.build())

                val eventToDelete = eventDao.getEventByRequestCode(requestCode)
                if (eventToDelete != null) {
                    eventDao.delete(eventToDelete)
                    Log.i("NotificationReceiver", "Deleted event with requestCode: $requestCode")
                } else {
                    Log.w("NotificationReceiver", "Event with requestCode $requestCode not found.")
                }
            } else if (!notificationPreference and (event != null)){
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
}
