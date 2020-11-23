package com.futurice.android.reservator.common;

import android.content.Context;
import android.content.SharedPreferences;

import com.futurice.android.reservator.model.platformcalendar
        .PlatformCalendarDataProxy;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;


/**
 * Created by shoj on 10/11/2016.
 */
public class PreferenceManager {
    private static PreferenceManager sharedInstance = null;

    public static PreferenceManager getInstance(Context context) {
        if (sharedInstance == null) {
            sharedInstance = new PreferenceManager(context);
        }
        return sharedInstance;
    }

    final static String PREFERENCES_IDENTIFIER = "ReservatorPreferences";

    final static String PREFERENCES_DEFAULT_ACCOUNT = "googleAccount";
    final static String PREFERENCES_DEFAULT_USER_NAME = "reservationAccount";
    final static String PREFERENCES_ADDRESSBOOK_ENABLED = "addressBookOption";
    final static String PREFERENCES_UNSELECTED_ROOMS = "unselectedRooms";
    final static String PREFERENCES_SELECTED_ROOM = "roomName";
    final static String PREFERENCES_CONFIGURED = "preferencedConfigured";
    final static String PREFERENCES_CALENDAR_MODE = "resourcesOnly";
    final static String PREFERENCES_DEFAULT_DURATION_MINUTES = "defaultDurationMinutes";
    final static String PREFERENCES_MAX_DURATION_MINUTES = "maxDurationMinutes";
    final static String PREFERENCES_SELECTED_LANGUAGE = "language";
    final static String PREFERENCES_ROOM_DISPLAY_NAME = "roomDisplayName";
    final static String PREFERENCES_CLOSING_HOURS = "closingHours";
    final static String PREFERENCES_CLOSING_MINUTES = "closingMinutes";
    final static String PREFERENCES_MQTT_SERVER_ADDRESS = "mqttServerAddress";
    final static String PREFERENCES_MQTT_PREFIX = "mqttPrefix";
    final static String PREFERENCES_MQTT_AIR_QUALITY_TOPIC = "mqttAirQualityTopic";
    final static String PREFERENCES_MQTT_CO2_TRESHOLD = "mqttCo2Treshold";

    final SharedPreferences preferences;

    private PreferenceManager(Context c) {
        preferences = c.getSharedPreferences(PREFERENCES_IDENTIFIER,
                                             Context.MODE_PRIVATE);
    }

    public String getDefaultCalendarAccount() {
        return preferences.getString(PREFERENCES_DEFAULT_ACCOUNT, null);
    }

    public void setDefaultCalendarAccount(String account) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCES_DEFAULT_ACCOUNT, account);
        editor.apply();
    }

    public String getDefaultUserName() {
        return preferences.getString(PREFERENCES_DEFAULT_USER_NAME, null);
    }

    public void setDefaultUserName(String user) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCES_DEFAULT_USER_NAME, user);
        editor.apply();
    }

    public boolean getAddressBookEnabled() {
        return preferences.getBoolean(PREFERENCES_ADDRESSBOOK_ENABLED, false);
    }

    public void setAddressBookEnabled(boolean enabled) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREFERENCES_ADDRESSBOOK_ENABLED, enabled);
        editor.apply();
    }

    public HashSet<String> getUnselectedRooms() {
        return (HashSet<String>) preferences
                .getStringSet(PREFERENCES_UNSELECTED_ROOMS,
                              new HashSet<String>());
    }


    public void setUnselectedRooms(HashSet<String> rooms) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putStringSet(PREFERENCES_UNSELECTED_ROOMS,
                            new HashSet<String>(rooms));
        editor.apply();
    }


    public String getSelectedRoom() {
        return preferences.getString(PREFERENCES_SELECTED_ROOM, null);
    }

    public void setSelectedRoom(String newRoom) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCES_SELECTED_ROOM, newRoom);
        editor.apply();
    }

    public String getSelectedLanguage() {
        return preferences.getString(PREFERENCES_SELECTED_LANGUAGE, Locale.getDefault().getLanguage());
    }

    public void setSelectedLanguage(String newLanguage) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCES_SELECTED_LANGUAGE, newLanguage);
        editor.apply();
    }

    public String getRoomDisplayName() {
        return preferences.getString(PREFERENCES_ROOM_DISPLAY_NAME, null);
    }

    public void setRoomDisplayName(String newName) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCES_ROOM_DISPLAY_NAME, newName);
        editor.apply();
    }

    public String getMqttServerAddress() {
        return preferences.getString(PREFERENCES_MQTT_SERVER_ADDRESS, null);
    }

    public void setMqttServerAddress(String newAddress) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCES_MQTT_SERVER_ADDRESS, newAddress);
        editor.apply();
    }

    public String getMqttPrefix() {
        return preferences.getString(PREFERENCES_MQTT_PREFIX, null);
    }

    public void setMqttPrefix(String newPrefix) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCES_MQTT_PREFIX, newPrefix);
        editor.apply();
    }

    public String getMqttAirQualityTopic() {
        return preferences.getString(PREFERENCES_MQTT_AIR_QUALITY_TOPIC, null);
    }

    public void setMqttAirQualityTopic(String newTopic) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCES_MQTT_AIR_QUALITY_TOPIC, newTopic);
        editor.apply();
    }

    public int getMqttCo2Treshold() {
        return preferences.getInt(PREFERENCES_MQTT_CO2_TRESHOLD, -1);
    }

    public void setMqttCo2Treshold(int value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREFERENCES_MQTT_CO2_TRESHOLD, value);
        editor.apply();
    }

    public int getDefaultDurationMinutes() {
        return preferences.getInt(PREFERENCES_DEFAULT_DURATION_MINUTES, 45);
    }

    public void setDefaultDurationMinutes(int value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREFERENCES_DEFAULT_DURATION_MINUTES, value);
        editor.apply();
    }

    public int getMaxDurationMinutes() {
        return preferences.getInt(PREFERENCES_MAX_DURATION_MINUTES, 120);
    }

    public void setMaxDurationMinutes(int value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREFERENCES_MAX_DURATION_MINUTES, value);
        editor.apply();
    }

    public void setClosingHours(int value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREFERENCES_CLOSING_HOURS, value);
        editor.apply();
    }


    public void setClosingMinutes(int value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREFERENCES_CLOSING_MINUTES, value);
        editor.apply();
    }

    public int getClosingHours() {
        return preferences.getInt(PREFERENCES_CLOSING_HOURS, -1);
    }

    public int getClosingMinutes() {
        return preferences.getInt(PREFERENCES_CLOSING_MINUTES, -1);
    }

    public boolean getApplicationConfigured() {
        return preferences.getBoolean(PREFERENCES_CONFIGURED, false);
    }

    public void setApplicationConfigured(boolean configured) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREFERENCES_CONFIGURED, configured);
        editor.apply();
    }


    public void removeAllSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        Map<String, ?> keys = preferences.getAll();
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            editor.remove(entry.getKey());
        }
        editor.apply();
    }

    public PlatformCalendarDataProxy.Mode getCalendarMode() {
        String name = preferences.getString(PREFERENCES_CALENDAR_MODE, null);
        if (name == null) {
            return null;
        }
        return Enum.valueOf(PlatformCalendarDataProxy.Mode.class, name);
    }

    public void setCalendarMode(PlatformCalendarDataProxy.Mode mode) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCES_CALENDAR_MODE, mode.name());
        editor.apply();
    }

}
