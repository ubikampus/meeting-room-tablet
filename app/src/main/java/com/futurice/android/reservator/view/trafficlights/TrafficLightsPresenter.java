package com.futurice.android.reservator.view.trafficlights;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.os.Handler;

import com.futurice.android.reservator.R;
import com.futurice.android.reservator.common.AirQualityListener;
import com.futurice.android.reservator.common.Helpers;
import com.futurice.android.reservator.common.LedHelper;
import com.futurice.android.reservator.common.MqttHelper;
import com.futurice.android.reservator.common.PreferenceManager;
import com.futurice.android.reservator.model.DateTime;
import com.futurice.android.reservator.model.Model;
import com.futurice.android.reservator.model.Reservation;
import com.futurice.android.reservator.model.ReservatorException;
import com.futurice.android.reservator.model.Room;
import com.futurice.android.reservator.model.TimeSpan;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Vector;

public class TrafficLightsPresenter implements
        TrafficLightsPageFragment.TrafficLightsPagePresenter,
        RoomStatusFragment.RoomStatusPresenter,
        RoomReservationFragment.RoomReservationPresenter,
        DayCalendarFragment.DayCalendarPresenter,
        OngoingReservationFragment.OngoingReservationPresenter,
        DisconnectedFragment.DisconnectedFragmentPresenter,
        BottomFragment.BottomFragmentPresenter,
        com.futurice.android.reservator.common.Presenter,
        com.futurice.android.reservator.model.DataUpdatedListener,
        com.futurice.android.reservator.model.AddressBookUpdatedListener,
                        AirQualityListener {

    private static final int STATE_FREE = 1;
    private static final int STATE_RESERVED = 2;

    final int QUICK_BOOK_THRESHOLD = 5; // minutes
    //final int MAX_QUICK_BOOK_MINUTES = 120; //minutes
   // final int DEFAULT_MINUTES = 45;

    private int state = -1;

    private String previousReservationId = null;
    private boolean connected = true;
    private boolean reservationChangeInProgess= false;
    private boolean tentativeChangeInProgess= false;

    private TrafficLightsPageFragment trafficLightsPageFragment;
    private RoomStatusFragment roomStatusFragment;
    private RoomReservationFragment roomReservationFragment;
    private OngoingReservationFragment ongoingReservationFragment;
    private DayCalendarFragment dayCalendarFragment;
    private DisconnectedFragment disconnectedFragment;
    private BottomFragment bottomFragment;

    private int co2Treshold = -1;
    private long lastAirQualityReadingTimeStamp = System.currentTimeMillis();

    private Activity activity;
    private Model model;
    private Resources resources;

    private Room room;
    private Reservation currentReservation;

    private Handler handler = new Handler();
    private Runnable minuteRunnable = new Runnable() {
        @Override
        public void run() {
            onMinuteElapsed();
            handler.postDelayed(this, 60000);
        }
    };

    // Get a handler that can be used to post to the main thread
    private Handler mainHandler;

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (room != null)
                updateRoomData(room);
        }
    };

    private void runThreadSafeUpdateRoomData() {
        if (this.mainHandler != null) {
            this.mainHandler.post(updateRunnable);
        }
    }

    private void refreshModel() {
        if (this.room != null)
            this.model.getDataProxy().refreshRoomReservations(this.room);
    }

    public void onMinuteElapsed() {
        if (this.roomStatusFragment != null) {
            if (lastAirQualityReadingTimeStamp < (System.currentTimeMillis()- (5*60000) )) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        roomStatusFragment.hideAirQualityWarning();
                        roomStatusFragment.hideCo2Text();
                    }
                });
            }
        }
        this.refreshModel();
        this.updateOngoingReservationFragment();
    }


    // State machine

    private void setState(int newState) {
        if (this.state == newState)
            return;

        else if (this.state == STATE_RESERVED && (newState != STATE_RESERVED
                || (newState == STATE_RESERVED && this.currentReservation != null && this.currentReservation.getId() != previousReservationId) )) {
            this.onReservationEnding();
        }

        else if ((this.state != STATE_RESERVED && newState == STATE_RESERVED)
                || (newState == STATE_RESERVED && this.currentReservation != null && this.currentReservation.getId() != previousReservationId) ) {
            this.onReservationStarting();
        }

        else
            this.reportReservationStatus();


        if (newState == STATE_RESERVED && this.currentReservation != null)
            this.previousReservationId = this.currentReservation.getId();

        this.state = newState;
    }

    private void reportReservationStatus() {
        String currentId = null;
        String currentTopic = null;
        long currentStartTime = -1;
        long currentEndTime = -1;

        String nextId = null;
        String nextTopic = null;
        long nextStartTime = -1;
        long nextEndTime = -1;

        if (currentReservation != null) {
            currentId = currentReservation.getId();
            currentTopic = resources.getString(R.string.status_reserved); //currentReservation.getSubject();
            currentStartTime = currentReservation.getStartTime().getTimeInMillis();
            currentEndTime = currentReservation.getEndTime().getTimeInMillis();
        }

        if (this.room != null) {
            Reservation nextReservation = null;
            if (currentReservation != null)
                nextReservation = this.room.getNextReservationToday(currentReservation.getEndTime(), currentId);
            else
                nextReservation = this.room.getNextReservationToday(new DateTime(), null);

            if (nextReservation != null)
                {
                    nextId = nextReservation.getId();
                    nextTopic = resources.getString(R.string.status_reserved); //nextReservation.getSubject();
                    nextStartTime = nextReservation.getStartTime().getTimeInMillis();
                    nextEndTime = nextReservation.getEndTime().getTimeInMillis();
                }
            }

        MqttHelper.getInstance(this.activity).reportReservationStatus(currentId, currentTopic, currentStartTime, currentEndTime,
                nextId, nextTopic, nextStartTime, nextEndTime);
    }

    private void onReservationStarting() {
        String currentId = null;
        String currentTopic = null;
        long currentStartTime = -1;
        long currentEndTime = -1;

        String nextId = null;
        String nextTopic = null;
        long nextStartTime = -1;
        long nextEndTime = -1;

        if (currentReservation != null) {
            currentId = currentReservation.getId();
            currentTopic = resources.getString(R.string.status_reserved); //currentReservation.getSubject();
            currentStartTime = currentReservation.getStartTime().getTimeInMillis();
            currentEndTime = currentReservation.getEndTime().getTimeInMillis();

            if (this.room != null) {
                Reservation nextReservation = this.room.getNextReservationToday(currentReservation.getEndTime(), currentId);
                if (nextReservation != null) {
                    nextId = nextReservation.getId();
                    nextTopic = resources.getString(R.string.status_reserved); //nextReservation.getSubject();
                    nextStartTime = nextReservation.getStartTime().getTimeInMillis();
                    nextEndTime = nextReservation.getEndTime().getTimeInMillis();
                }
            }
        }


        MqttHelper.getInstance(this.activity).reportReservationStarting(currentId, currentTopic, currentStartTime, currentEndTime,
                nextId, nextTopic, nextStartTime, nextEndTime);
    }

    private void onReservationEnding() {
        String currentId = null;
        String currentTopic = null;
        long currentStartTime = -1;
        long currentEndTime = -1;

        String nextId = null;
        String nextTopic = null;
        long nextStartTime = -1;
        long nextEndTime = -1;

        if (currentReservation != null) {
            currentId = currentReservation.getId();
            currentTopic = resources.getString(R.string.status_reserved); //currentReservation.getSubject();
            currentStartTime = currentReservation.getStartTime().getTimeInMillis();
            currentEndTime = currentReservation.getEndTime().getTimeInMillis();

            if (this.room != null) {
                Reservation nextReservation = this.room.getNextReservationToday(currentReservation.getEndTime(), currentId);;
                if (nextReservation != null) {
                    nextId = nextReservation.getId();
                    nextTopic = resources.getString(R.string.status_reserved); //nextReservation.getSubject();
                    nextStartTime = nextReservation.getStartTime().getTimeInMillis();
                    nextEndTime = nextReservation.getEndTime().getTimeInMillis();
                }
            }
        }

        MqttHelper.getInstance(this.activity).reportReservationEnding(currentId, currentTopic, currentStartTime, currentEndTime,
                                                                    nextId, nextTopic, nextStartTime, nextEndTime);
    }

    // State machine ends



    public TrafficLightsPresenter(Activity activity, Model model) {
        this.activity = activity;
        this.resources = activity.getResources();

        mainHandler = new Handler(activity.getMainLooper());
        this.model = model;
        this.model.getDataProxy().addDataUpdatedListener(this);
        this.model.getAddressBook().addDataUpdatedListener(this);

    }

    private boolean isStarted() {
        if (trafficLightsPageFragment != null && roomStatusFragment != null &&
                ongoingReservationFragment != null && roomReservationFragment != null && dayCalendarFragment != null &&
                disconnectedFragment!=null)
            return true;
        else
            return false;
    }
    private void tryStarting() {
        if (trafficLightsPageFragment != null && roomStatusFragment != null &&
                ongoingReservationFragment != null && roomReservationFragment != null && dayCalendarFragment != null &&
                disconnectedFragment != null && this.bottomFragment != null){

            this.co2Treshold = PreferenceManager.getInstance(this.activity).getMqttCo2Treshold();
            MqttHelper.getInstance(this.activity).setAirQualityListener(this);
            this.model.getDataProxy().refreshRoomReservations(this.model.getFavoriteRoom());
            handler.postDelayed(minuteRunnable, 60000);
        }
    }

    private void makeReservation(TimeSpan timespan, String description) {
        try {
            String accountEmail = PreferenceManager.getInstance(this.activity).getDefaultUserName();
            this.model.getDataProxy().reserve(room, timespan, description, accountEmail);
            this.refreshModel();
        } catch (ReservatorException e) {
            Log.d("Reservator", e.toString());
        }
    }

    private void cancelCurrentReservation() {
        if (currentReservation == null)
            return;
        try {
            this.model.getDataProxy().cancelReservation(this.currentReservation);
            this.refreshModel();
        } catch (ReservatorException e) {
                Log.d("Reservator", e.toString());
            }
    }

    private void modifyCurrentReservationTimeSpan(TimeSpan timeSpan) {
        if (currentReservation == null)
            return;
        try {
            this.model.getDataProxy().modifyReservationTimeSpan(this.currentReservation, this.room, timeSpan);
            this.refreshModel();
        } catch (ReservatorException e) {
            Log.d("Reservator", e.toString());
        }
    }

    private void showInfoWindow() {
        this.trafficLightsPageFragment.showInfoWindow(this.activity);
    }

    // ------ Implementation of RoomReservationFragment.RoomReservationPresenter

    @Override
    public void setRoomReservationFragment(RoomReservationFragment fragment) {
        this.roomReservationFragment = fragment;
        this.roomReservationFragment.setMaxMinutes(PreferenceManager.getInstance(fragment
            .getContext()).getMaxDurationMinutes());
        this.roomReservationFragment.setMinutes(PreferenceManager.getInstance(fragment.getContext
            ()).getDefaultDurationMinutes());
        this.tryStarting();
    }

    @Override
    public void onReservationRequestMade(int minutes, String description) {
        Log.d("", "TrafficLightsPresenter::onReservationRequestMade() minutes: " + minutes + " description: " + description);

        TimeSpan timeSpan = new TimeSpan(new DateTime(), new DateTime(System.currentTimeMillis() + (minutes * 60 * 1000)));
        String tempDescription = resources.getString(R.string.default_reservation_description);

        if (description != null && !"".equals(description))
            tempDescription = description;

        this.makeReservation(timeSpan, tempDescription);
    }

    @Override
    public void onMinutesUpdated(int minutes) {
        //Log.d("debug","onMinutesUpdated: "+minutes);
        if (this.currentReservation == null) {
            this.dayCalendarFragment.setTentativeTimeSpan(new TimeSpan(new DateTime(), new DateTime(System.currentTimeMillis() + (minutes * 60 * 1000))));
            this.dayCalendarFragment.updateRoomData(this.room);
        }
    }

    @Override
    public void onTentativeChangeStarted() {
        this.tentativeChangeInProgess = true;
    }

    @Override

    public void onTentativeChangeEnded() {
        this.tentativeChangeInProgess = false;
    }

    // ------ Implementation of OngoingReservationFragment.OngoingReservationPresenter

    @Override
    public void setOngoingReservationFragment(OngoingReservationFragment fragment) {
        this.ongoingReservationFragment = fragment;

        //this.roomReservationFragment.setTimeLimits(System.currentTimeMillis(), System.currentTimeMillis() + 1000*60*120);
        this.tryStarting();
    }

    @Override
    public void onReservationMinutesChanged(int newMinutes) {
        //if (newMinutes <= 0) {
        //    this.cancelCurrentReservation();
        //}
        //else {

            DateTime startTime = this.currentReservation.getStartTime();

            DateTime newEndTime = new DateTime(System.currentTimeMillis() + (newMinutes* 60 * 1000));
            this.modifyCurrentReservationTimeSpan(new TimeSpan(startTime,newEndTime));
        //}
    }

    @Override
    public void onReservationMinutesUpdated(int minutes) {
        if (currentReservation == null)
            return;
        this.currentReservation.setTimeSpan(new TimeSpan(this.currentReservation.getStartTime(), new DateTime(System.currentTimeMillis() + (minutes * 60 * 1000))));
        this.dayCalendarFragment.updateRoomData(this.room);
    }

    @Override
    public void onReservationChangeStarted() {
       this.reservationChangeInProgess = true;
    }

    @Override

    public void onReservationChangeEnded() {
        this.reservationChangeInProgess = false;
    }

    // ------ Implementation of TrafficLightsPageFragment.TrafficLightsPagePresenter

    @Override
    public void setTrafficLightsPageFragment(TrafficLightsPageFragment fragment) {
        this.trafficLightsPageFragment = fragment;
        this.tryStarting();
    }

    // ------- Implementation of RoomStatusFragment.RoomStatusPresenter

    @Override
    public void setRoomStatusFragment(RoomStatusFragment fragment) {
        this.roomStatusFragment = fragment;
        this.tryStarting();
    }

    // ------- Implementation of DayCalendarFragment.DayCalendarPresenter

    @Override
    public void setDayCalendarFragment(DayCalendarFragment fragment) {
        this.dayCalendarFragment = fragment;
        this.tryStarting();
    }

    // ------- Implementation of DisconnectedFragment.DisconnectedFragmentPresenter

    @Override
    public void setDisconnectedFragment(DisconnectedFragment fragment) {
        this.disconnectedFragment = fragment;
        this.tryStarting();
    }


    // ------- Implementation of BottonFragment.BottomFragmentPresenter

    @Override
    public void setBottomFragment(BottomFragment fragment) {
        this.bottomFragment = fragment;
        this.tryStarting();
    }

    @Override
    public void onInfoButtonClicked() {
        this.showInfoWindow();
    }

    // ------- Implementation of model.DataUpdatedListener

    @Override
    public void roomListUpdated(ArrayList<Room> rooms) {

    }

    @Override
    public void roomReservationsUpdated(Room room) {
        this.updateRoomData(room);
        //this.room = room;
        //runThreadSafeUpdateRoomData();
    }

    @Override
    public void refreshFailed(ReservatorException ex) {

    }

    // ------- Implementation of model.AddressBookUpdatedListener

    @Override
    public void addressBookUpdated() {

    }

    @Override
    public void addressBookUpdateFailed(ReservatorException e) {

    }


    private int getMinutesUntilClosingTime() {
        int closingHours = PreferenceManager.getInstance(activity).getClosingHours();       //closingTime is in minutes since midnight
        int closingMinutes = PreferenceManager.getInstance(activity).getClosingMinutes();       //closingTime is in minutes since midnight

        if (closingHours == -1 || closingMinutes == -1)
            return Integer.MAX_VALUE;


        java.util.Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, closingHours);
        cal.set(Calendar.MINUTE, closingMinutes);

        java.util.Calendar currentCal = Calendar.getInstance();

        long differenceInMillis = cal.getTimeInMillis() - currentCal.getTimeInMillis();

        int differenceInMinutes = (int)differenceInMillis / (1000*60);

        if (differenceInMinutes < 0)
            differenceInMinutes = 0;

        return differenceInMinutes;
    }


    private void showReservationDetails(Reservation r, TimeSpan nextFreeSlot) {
        if (r == null) {
            this.roomStatusFragment.setMeetingNameText("");
        } else {
            this.roomStatusFragment.setMeetingNameText(r.getSubject());
        }

        if (nextFreeSlot == null) {
            // More than a day away
            this.roomStatusFragment.setStatusUntilText("");
        } else {
            String temp = resources.getString(R.string.free_at);
            this.roomStatusFragment.setStatusUntilText(Html.fromHtml(String.format(
                    Locale.getDefault(),
                    temp + " <b>%02d:%02d</b>",
                    nextFreeSlot.getStart().get(Calendar.HOUR_OF_DAY),
                    nextFreeSlot.getStart().get(Calendar.MINUTE))).toString());
        }
    }

    private void updateOngoingReservationFragment() {
        if (this.room == null)
            return;

        if (currentReservation == null)
            return;


        long endTime = currentReservation.getEndTime().getTimeInMillis();
        int remainingMinutes = (int) Math.round((endTime - System.currentTimeMillis()) / 60000f);
        int tempMax = PreferenceManager.getInstance(activity).getMaxDurationMinutes();

        // Maximum time until the next reservation
        if (room.isFreeAt(currentReservation.getEndTime())) {
            int freeAfterThis = room.minutesFreeFrom(currentReservation.getEndTime());

            if (freeAfterThis < Integer.MAX_VALUE)
                tempMax = remainingMinutes + room.minutesFreeFrom(currentReservation.getEndTime());
            else
                tempMax = Integer.MAX_VALUE;
        }
        else {      //back to back reservations
            tempMax = remainingMinutes;
        }

        if (tempMax > PreferenceManager.getInstance(activity).getMaxDurationMinutes())
            tempMax = PreferenceManager.getInstance(activity).getMaxDurationMinutes();

        if (remainingMinutes > tempMax)
            tempMax = remainingMinutes;

        int minutesUntilClosingTime = this.getMinutesUntilClosingTime();
        if (minutesUntilClosingTime != Integer.MAX_VALUE && minutesUntilClosingTime < tempMax )
            tempMax = minutesUntilClosingTime;


        if (this.ongoingReservationFragment != null) {
            if (currentReservation.getDuration() != null)
                this.ongoingReservationFragment.setNotModifiable();

            else
                this.ongoingReservationFragment.setModifiable();

            this.ongoingReservationFragment.setMaxMinutes(tempMax);
            if (!this.reservationChangeInProgess)
                this.ongoingReservationFragment.setRemainingMinutes(remainingMinutes);

        }

        if (remainingMinutes <= 0) {
            this.refreshModel();
        }
    }

    private void showYellowLed() {
        LedHelper.getInstance().setGreenBrightness(255);
        LedHelper.getInstance().setRedBrightness(255);
    }

    private void showGreenLed() {
        LedHelper.getInstance().setGreenBrightness(255);
        LedHelper.getInstance().setRedBrightness(0);
    }

    private void showRedLed() {
        LedHelper.getInstance().setGreenBrightness(0);
        LedHelper.getInstance().setRedBrightness(255);
    }

    private void showReserved() {
        this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightReserved));
        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_reserved));
        this.dayCalendarFragment.setTentativeTimeSpan(null);
        this.updateOngoingReservationFragment();

        if (this.connected)
            this.trafficLightsPageFragment.showOngoingReservationFragment();
        else
            this.trafficLightsPageFragment.showDisconnectedFragment();

        //this.roomStatusFragment.hideBookNowText();
        this.showReservationDetails(this.currentReservation, room.getNextFreeSlot());

        this.roomReservationFragment.clearDescription();

        this.roomReservationFragment.setMaxMinutes(PreferenceManager.getInstance(activity).getMaxDurationMinutes());
        this.roomReservationFragment.setMinutes(PreferenceManager.getInstance(activity).getDefaultDurationMinutes());

        this.showRedLed();
    }

    private void showReservationPending(int freeMinutes, DateTime freeAt) {
        this.currentReservation = null;
        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
        this.roomStatusFragment.setStatusUntilText(resources.getString(R.string.free_for_specific_amount)+" "+Helpers.dateTimeTo24h(freeAt));
        this.roomStatusFragment.setMeetingNameText("");

        this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightYellow));

        //this.trafficLightsPageFragment.showOngoingReservationFragment();
        //this.updateOngoingReservationFragment();
        //this.roomStatusFragment.hideBookNowText();

        if (this.connected)
            this.trafficLightsPageFragment.hideBothReservationFragments();
        else
            this.trafficLightsPageFragment.showDisconnectedFragment();

        this.showYellowLed();
    }

    private void showFreeForRestOfTheDay() {
        this.currentReservation = null;
        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
        this.roomStatusFragment.setMeetingNameText("");
        this.roomStatusFragment.setStatusUntilText(resources.getString(R.string.free_for_the_day));

        int tempMinutes = PreferenceManager.getInstance(activity).getMaxDurationMinutes();

        int minutesUntilClosingTime = this.getMinutesUntilClosingTime();
        if (minutesUntilClosingTime != Integer.MAX_VALUE && minutesUntilClosingTime < tempMinutes )
            tempMinutes = minutesUntilClosingTime;

        int oldMaxMinutes = roomReservationFragment.getMaxMinutes();

        this.roomReservationFragment.setMaxMinutes(tempMinutes);

        // If the maximum minutes is being increased, the closing time has passed
        // and the seekbar can be set to defaultDurationMinutes

        if (oldMaxMinutes < tempMinutes && !tentativeChangeInProgess) {
            int defaultDuration = PreferenceManager.getInstance(activity).getDefaultDurationMinutes();

            int defaultDur  = defaultDuration > tempMinutes ? tempMinutes : defaultDuration;
            this.roomReservationFragment.setMinutes(defaultDur);
        }

        int minutes = this.roomReservationFragment.getCurrentMinutes();
        this.dayCalendarFragment.setTentativeTimeSpan(new TimeSpan(new DateTime(), new DateTime(System.currentTimeMillis() + (minutes * 60 * 1000))));

        this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightFree));

        if (this.connected) {
            this.trafficLightsPageFragment.showRoomReservationFragment();
            //this.roomStatusFragment.showBookNowText();
        }
        else {
            this.trafficLightsPageFragment.showDisconnectedFragment();
            //this.roomStatusFragment.hideBookNowText();
        }
        this.showGreenLed();
    }

    private void showFreeForMinutes(int freeMinutes, DateTime freeAt) {
        this.currentReservation = null;
        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
        this.roomStatusFragment.setStatusUntilText(resources.getString(R.string.free_for_specific_amount)+" "+Helpers.dateTimeTo24h(freeAt));

        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
        this.roomStatusFragment.setMeetingNameText("");

        this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightFree));
        this.trafficLightsPageFragment.showRoomReservationFragment();
        //this.roomStatusFragment.showBookNowText();

        int tempMinutes = PreferenceManager.getInstance(activity).getMaxDurationMinutes();

        if (freeMinutes < PreferenceManager.getInstance(activity).getMaxDurationMinutes())
            tempMinutes = freeMinutes;

        int minutesUntilClosingTime = this.getMinutesUntilClosingTime();
        if (minutesUntilClosingTime != Integer.MAX_VALUE && minutesUntilClosingTime < tempMinutes )
            tempMinutes = minutesUntilClosingTime;

        int oldMaxMinutes = roomReservationFragment.getMaxMinutes();

        this.roomReservationFragment.setMaxMinutes(tempMinutes);

        // If the maximum minutes is being increased, the closing time has passed
        // and the seekbar can be set to defaultDurationMinutes

        if (oldMaxMinutes < tempMinutes && !this.tentativeChangeInProgess) {
            int defaultDuration = PreferenceManager.getInstance(activity).getDefaultDurationMinutes();

            int defaultDur  = defaultDuration > tempMinutes ? tempMinutes : defaultDuration;
            this.roomReservationFragment.setMinutes(defaultDur);
        }

        int minutes = this.roomReservationFragment.getCurrentMinutes();
        this.dayCalendarFragment.setTentativeTimeSpan(new TimeSpan(new DateTime(), new DateTime(System.currentTimeMillis() + (minutes * 60 * 1000))));


        if (this.connected)
            this.trafficLightsPageFragment.showRoomReservationFragment();
        else
            this.trafficLightsPageFragment.showDisconnectedFragment();

        this.showGreenLed();
    }


    public void updateRoomData(Room room) {
        if (!isStarted())
            return;

        this.room = room;


        if (PreferenceManager.getInstance(this.activity).getRoomDisplayName() != null) {
            this.roomStatusFragment.setRoomTitleText(PreferenceManager.getInstance(this.activity).getRoomDisplayName());
        }
        else {
            this.roomStatusFragment.setRoomTitleText(room.getName());
        }
        Reservation tempCurrentReservation = room.getCurrentReservation();
        if ( tempCurrentReservation == null) {
            this.setState(STATE_FREE);
            if (room.isFreeRestOfDay()) {
                this.showFreeForRestOfTheDay();
            } else {
                int freeMinutes = room.minutesFreeFromNow();
                DateTime freeAt = room.getNextFreeTime().getEnd();
                if (freeMinutes >= Room.RESERVED_THRESHOLD_MINUTES) {
                    this.showFreeForMinutes(freeMinutes, freeAt);
                } else {
                    this.showReservationPending(freeMinutes, freeAt);
                }
            }
        } else {
            this.currentReservation = tempCurrentReservation;
            this.setState(STATE_RESERVED);
            this.showReserved();
        }
        if (!this.reservationChangeInProgess)
            this.dayCalendarFragment.updateRoomData(room);
    }

    public void setConnected() {
        Log.d("Reservator","TrafficLightsPresenter::setConnected()");
        this.connected = true;
        runThreadSafeUpdateRoomData();
    }

    public void setDisconnected() {
        Log.d("Reservator","TrafficLightsPresenter::setDisconnected()");
        this.connected = false;
        runThreadSafeUpdateRoomData();
    }

    @Override
    public void onAirQualityReading(final int co2Reading, final int vocReading) {
        Log.d("reservator", "onAirQualityReading() "+ co2Reading);

        lastAirQualityReadingTimeStamp = System.currentTimeMillis();

        if (this.roomStatusFragment!=null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    roomStatusFragment.showCo2text(co2Reading);
                }
            });

            if (co2Treshold !=- 1) {
                if (co2Reading >= co2Treshold) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            roomStatusFragment.showAirQualityWarning(co2Treshold);
                        }
                    });
                }
                if (co2Reading < co2Treshold) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            roomStatusFragment.hideAirQualityWarning();
                        }
                    });
                }
            }

        }
    }
}
