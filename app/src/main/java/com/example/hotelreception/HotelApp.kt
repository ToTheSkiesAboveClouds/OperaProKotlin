package com.example.hotelreception

import android.app.Application

// Класс Application создаётся один раз при запуске процесса, раньше любой Activity.
// Здесь открывается база данных Room и создаётся единственный репозиторий.
class HotelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HotelRepository.init(this)
    }
}
